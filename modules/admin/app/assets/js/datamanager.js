"use strict";

// Prevent default drag/drop action...
window.addEventListener("dragover",function(e){
  e = e || event;
  e.preventDefault();
},false);
window.addEventListener("drop",function(e){
  e = e || event;
  e.preventDefault();
},false);

/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  ajaxHeaders: {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  },

  /**
   *
   * @param obj an object of URL parameters
   * @returns {string}
   */
  objToQueryString: function (obj) {
    let str = [];
    for (var p in obj)
      if (obj.hasOwnProperty(p)) {
        if (Array.isArray(obj[p])) {
          obj[p].forEach(v => {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
          });
        } else {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }
    return str.join("&");
  },

  listFiles: function (prefix) {
    return fetch(SERVICE.listFiles(CONFIG.repositoryId, prefix).url)
      .then(r => r.json());
  },

  validateFiles: function (paths) {
    return SERVICE.validateFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  ingestFiles: function (paths) {
    return SERVICE.ingestFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  deleteFiles: function (paths) {
    return SERVICE.deleteFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  deleteAll: function() {
    return SERVICE.deleteAll(CONFIG.repositoryId).ajax({
      headers: this.ajaxHeaders
    }).then(data => data.ok || false);
  },

  fileUrls: function (paths) {
    return SERVICE.fileUrls(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  uploadHandle: function(fileSpec) {
    return SERVICE.uploadHandle(CONFIG.repositoryId).ajax({
      data: JSON.stringify(fileSpec),
      headers: this.ajaxHeaders
    });
  }
};

Vue.component("files-table", {
  props: {
    loaded: Boolean,
    files: Array,
    deleting: Object,
    ingesting: Object,
    validating: Object,
    validationLog: Object,
  },
  data: function() {
    return {

    }
  },
  methods: {
    // EJohn's pretty date:
    // Takes an ISO time and returns a string representing how
    // long ago the date represents.
    // https://johnresig.com/blog/javascript-pretty-date/
    prettyDate: function (time) {
      var date = new Date((time || "").replace(/-/g, "/").replace(/[TZ]/g, " ")),
        diff = (((new Date()).getTime() - date.getTime()) / 1000),
        day_diff = Math.floor(diff / 86400);

      if (isNaN(day_diff) || day_diff < 0 || day_diff >= 31)
        return;

      return day_diff === 0 && (
        diff < 60 && "just now" ||
        diff < 120 && "1 minute ago" ||
        diff < 3600 && Math.floor(diff / 60) + " minutes ago" ||
        diff < 7200 && "1 hour ago" ||
        diff < 86400 && Math.floor(diff / 3600) + " hours ago") ||
        day_diff === 1 && "Yesterday" ||
        day_diff < 7 && day_diff + " days ago" ||
        day_diff < 31 && Math.ceil(day_diff / 7) + " weeks ago";
    },

    isValid: function(key) {
      let err = this.validationLog[key];
      if (_.isArray(err)) {
        return err.length === 0;
      }
      return null;
    },
  },
  template: `
    <div id="file-list-container">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <td>Name</td>
          <td>Last Modified</td>
          <td>Size</td>
          <td colspan="4"></td>
        </tr>
        </thead>
        <tbody>
        <tr v-for="file in files">
          <td>{{file.key}}</td>
          <td>{{prettyDate(file.lastModified)}}</td>
          <td>{{file.size}}</td>
          <td>
            <a href="#" v-on:click.prevent="$emit('delete-file', file.key)">
              <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': deleting[file.key], 
                'fa-trash-o': !deleting[file.key] 
              }"></i>
            </a>
          </td>
          <td>
            <a href="#" v-bind:disabled="validating[file.key]" v-on:click.prevent="$emit('validate-file', file.key)">
              <i class="fa fa-fw fa-spin fa-circle-o-notch" v-if="validating[file.key]"/>
              <i class="fa fa-fw fa-check-circle-o" v-else-if="isValid(file.key)"/>
              <i class="fa fa-fw fa-exclamation-triangle" v-else-if="isValid(file.key) === false"/>
              <i class="fa fa-fw fa-question-circle-o" v-else/>
            </a>
          </td>
          <td><a href="#" v-on:click.prevent="$emit('show-preview', file.key)"><i class="fa fa-eye"></i></a></td>
          <td><a href="#" v-on:click.prevent="$emit('ingest-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
              'fa-database': !ingesting[file.key], 
              'fa-circle-o-notch fa-spin': ingesting[file.key]
            }"></i></a>
          </td>
        </tr>
        </tbody>
      </table>
      <div class="admin-help-notice" v-else-if="loaded && files.length === 0">
        There are no files yet.
      </div>
    </div>
  `
});

var app = new Vue({
  el: '#data-manager',
  data: function () {
    return {
      loaded: false,
      truncated: false,
      files: [],
      deleting: {},
      selected: [],
      dropping: false,
      uploading: [],
      cancelled: [],
      log: [],
      ingesting: {},
      validating: {},
      validationLog: {},
      lastValidated: null,
      tab: 'upload',
      previewing: null,
      previewData: null,
      previewTruncated: false,
    }
  },
  watch: {
  },
  methods: {
    refresh: function () {
      return DAO.listFiles("").then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      });
    },
    deleteFile: function(key) {
      this.$set(this.deleting, key, true);
      DAO.deleteFiles([key]).then(deleted => {
        deleted.forEach(key => this.$delete(this.deleting, key));
        this.refresh();
      })
    },
    deleteAll: function() {
      this.files.forEach(f => this.$set(this.deleting, f.key, true));
      return DAO.deleteAll().then(r => {
        this.refresh();
        this.deleting = {};
        r;
      });
    },
    ingestAll: function() {

    },
    finishUpload: function(fileSpec) {
      this.setUploadProgress(fileSpec, 100);
      setTimeout(() => {
        let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
        this.uploading.splice(i, 1)
      }, 1000);
    },
    setUploadProgress: function(fileSpec, percent) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      if (i > -1) {
        this.uploading[i].progress = Math.min(100, percent);
        return true;
      }
      return false;
    },
    showPreview: function(key) {
      this.previewing = key;
      DAO.fileUrls([key]).then(data => {
        fetch(data[key]).then(r => {
          let reader = r.body.getReader();
          let self = this;
          let decoder = new TextDecoder("utf-8");
          reader.read().then(function appendBody({done, value}) {
            if (!done) {
              if (self.previewData !== null && self.previewData.length > 10) {
                self.previewTruncated = true;
                reader.cancel();
              } else {
                let text = decoder.decode(value);
                if (self.previewData === null) {
                  self.previewData = text;
                } else {
                  self.previewData += text;
                }
                reader.read().then(appendBody);
              }
            }
          });
        });
      });
    },
    closePreview: function() {
      this.previewing = null;
      this.previewData = null;
    },
    validateFile: function(key) {
      this.$set(this.validating, key, true);
      DAO.validateFiles([key]).then(e => {
        let errors = e[key];
        this.$set(this.validationLog, key, errors);
        this.$delete(this.validating, key);
        this.lastValidated = key;
      });
    },
    dragOver: function(event) {
      this.dropping = true;
    },
    dragLeave: function(event) {
      this.dropping = false;
    },
    uploadFile: function(file) {
      // Check we're still in the queue and have not been cancelled...
      if (_.findIndex(this.uploading, f => f.spec.name === file.name) === -1) {
        return Promise.resolve();
      }

      let self = this;

      return DAO.uploadHandle({
        name: file.name,
        type: file.type,
        size: file.size
      }).then(data => {
        return new Promise((resolve, reject) => {
          let url = data.presignedUrl;
          self.setUploadProgress(file, 0);
          let xhr = new XMLHttpRequest();
          xhr.overrideMimeType(file.type);

          xhr.upload.addEventListener("progress", evt => {
            if (evt.lengthComputable) {
              if (!self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))) {
                // the upload has been cancelled...
                xhr.abort();
              }
            }
          });
          xhr.addEventListener("load", evt => {
            if (xhr.readyState === xhr.DONE && xhr.status === 200) {
              self.finishUpload(file);
              self.refresh().then(_ => {
                resolve(xhr.responseXML);
              });
              console.log(xhr.responseXML);
            } else {
              reject(xhr.responseText);
            }
          });
          xhr.addEventListener("error", evt => {
            reject(xhr.responseText);
          });
          xhr.addEventListener("abort", evt => {
            resolve(xhr.responseText);
          });

          xhr.open("PUT", url);
          xhr.setRequestHeader("Content-Type", file.type);
          xhr.send(file);
        });
      });
    },
    uploadFiles: function(event) {
      this.dragLeave(event);
      let self = this;

      let fileList = event.dataTransfer
        ? event.dataTransfer.files
        : event.target.files;

      function sequential(arr, index) {
        if (index >= arr.length) return Promise.resolve();
        return self.uploadFile(arr[index])
          .then(r => {
            return sequential(arr, index + 1)
          });
      }

      let files = [];
      for (let i = 0; i < fileList.length; i++) {
        let file = fileList[i];
        if (file.type === "text/xml") {
          this.uploading.push({
            spec: file,
            progress: 0,
          });
          files.push(file);
        }
      }

      // Files were dropped but there were no file ones
      if (files.length === 0 && fileList.length > 0) {
        return Promise.reject("No valid files found")
      }

      // Nothing is selected: no-op
      if (files.length === 0) {
        return Promise.resolve();
      }

      // Proceed with upload
      return sequential(files, 0)
        .then(_ => {
          if (event.target.files) {
            // Delete the value of the control, if loaded
            event.target.value = null;
          }

          console.log("Files uploaded...")
        });
    },
    ingestFiles: function(keys) {
      let self = this;
      this.tab = "ingest";
      keys.forEach(key => {
        this.$set(this.ingesting, key, true);
      });
      DAO.ingestFiles(keys)
        .then(data => {
          if (data.url && data.jobId) {
            var websocket = new WebSocket(data.url);
            websocket.onopen = function() {
              console.debug("Opened monitoring socket...")
            };
            websocket.onerror = function(e) {
              self.log.push("ERROR. Try refreshing the page. ");
              console.error("Socket error!", e);
              keys.forEach(key => {
                self.$delete(self.ingesting, key);
              });
            };
            websocket.onclose = function() {
              console.debug("Closed!");
            };
            websocket.onmessage = function(e) {
              var msg = JSON.parse(e.data);
              self.log.push(msg.trim());
              if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
                websocket.close();
                keys.forEach(key => {
                  self.$delete(self.ingesting, key);
                });
                console.debug("Closed socket")
              }
              // FIXME
              let logElem = document.getElementById("update-progress");
              logElem.scrollTop = logElem.clientHeight;
            };

          } else {
            console.error("Unexpected job data", data);
          }
        });
    },
  },
  computed: {
  },
  created: function () {
    this.refresh();
  },
  template: `
    <div id="data-manager-container">
      <div id="actions-menu" class="downdown">
        <a href="#" id="actions-menu-toggle" class="btn btn-default dropdown-toggle pull-right" role="button" 
            data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
          Actions
        </a>
        <div class="dropdown-menu" aria-labelledby="actions-menu-toggle">
          <a href="#" class="dropdown-item" v-on:click.prevent="deleteAll()">
            Delete All
          </a>
        </div>
      </div>
      
      <files-table
        v-bind:loaded="loaded"
        v-bind:files="files"
        v-bind:validating="validating"
        v-bind:validationLog="validationLog"
        v-bind:deleting="deleting"
        v-bind:ingesting="ingesting"
        
        v-on:delete-file="deleteFile"
        v-on:ingest-files="ingestFiles"
        v-on:validate-file="validateFile"
        v-on:show-preview="showPreview"
       /> 
      
      <div id="status">
        <ul id="status-panel-tabs" class="nav nav-tabs">
          <li class="nav-item">
            <a href="#tab-file-upload" class="nav-link" v-bind:class="{'active': tab === 'upload'}"
               v-on:click.prevent="tab = 'upload'">
              Upload Files
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-validation-errors" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
               v-on:click.prevent="tab = 'validation'">
              Validation
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-ingest-log" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
               v-on:click.prevent="tab = 'ingest'">
              Ingest
            </a>
          </li>
        </ul>

        <div id="status-panels">
          <div class="status-panel" id="tab-validation-errors" v-show="tab === 'validation'">
            <pre v-if="lastValidated"><template v-for="msg in validationLog[lastValidated]">{{msg}}</template></pre>
          </div>
          <div class="status-panel" id="tab-ingest-log" v-show="tab === 'ingest'">
            <pre v-if="log.length > 0"><template v-for="msg in log">{{msg}}<br/></template></pre>
          </div>
          <div class="status-panel" id="tab-file-upload" v-show="tab === 'upload'" v-bind:class="{dropping: dropping}">
            <input id="file-selector"
                   v-on:change="uploadFiles"
                   v-on:dragover.prevent="dragOver"
                   v-on:dragleave.prevent="dragLeave"
                   v-on:drop.prevent="uploadFiles"
                   type="file"
                   accept="text/xml" multiple/>
            Click to select or drop files here...
          </div>

          <div class="modal show" v-if="previewing" role="dialog" style="display: block">
            <div class="modal-dialog" role="document">
              <div class="modal-content">
                <div class="modal-header">
                  <h3 class="modal-title">{{previewing}}</h3>
                  <button type="button" class="close" data-dismiss="modal" aria-label="Close"
                          v-on:click="closePreview()">
                    <span aria-hidden="true">&times;</span>
                  </button>
                </div>
                <div class="modal-body" id="preview-data" v-bind:class="{'loading':previewData === null}">
                  <pre>{{previewData}}
                    <template v-if="previewTruncated"><br/>... [truncated] ...</template></pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div id="upload-progress" v-if="uploading.length > 0">
        <div v-for="job in uploading" class="progress-container">
          <div class="progress">
            <div class="progress-bar progress-bar-striped progress-bar-animated"
                 role="progressbar"
                 v-bind:aria-valuemax="100"
                 v-bind:aria-valuemin="0"
                 v-bind:aria-valuenow="job.progress"
                 v-bind:style="'width: ' + job.progress + '%'">
              {{ job.spec.name}}
            </div>
          </div>
          <button class="cancel-button" v-on:click.prevent="finishUpload(job.spec)">
            <i class="fa fa-fw fa-times-circle"/>
          </button>
        </div>
      </div>
    </div>
  `
});