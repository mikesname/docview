"use strict";

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(() => sequential(func, arr, index + 1));
}

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function (bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("stageName", function(code, config) {
  switch (code) {
    case "oaipmh": return "Harvesting";
    case "upload": return "Uploads";
    case "rs": return "ResourceSync";
    default: return code;
  }
});

Vue.filter("decodeUri", function(s) {
  return decodeURI(s);
});

Vue.filter("prettyDate", function (time) {
  let f = time => {
    let m = luxon.DateTime.fromISO(time);
    return m.isValid ? m.toRelative() : "";
  };
  return _.memoize(f)(time);
});

let previewMixin = {
  data: function() {
    return {
      tab: 'preview'
    }
  },
  methods: {
    showPreview: function (file) {
      this.previewing = file;
      this.tab = 'preview';
    },
  }
};

let utilMixin = {
  methods: {
    removeQueryParam: function(qs, names) {
      let qp = this.queryParams(qs);
      return this.queryString(_.omit(qp, names));
    },
    setQueryParam: function(qs, name, value) {
      let qp = this.queryParams(qs);
      return this.queryString(_.set(qp, name, value));
    },
    getQueryParam: function(qs, name) {
      let qp = this.queryParams(qs);
      return _.has(qp, name) ? qp[name] : null;
    },
    queryParams: function(qs) {
      let qsp = (qs && qs[0] === '?') ? qs.slice(1): qs;
      return (qsp && qsp.trim() !== "")
        ? _.fromPairs(qsp.split("&").map(p => p.split("=")))
        : {};
    },
    queryString: function(qp) {
      return !_.isEmpty(qp)
        ? ("?" + _.toPairs(qp).map(p => p.join("=")).join("&"))
        : "";
    },
    removeUrlState: function(key) {
      history.replaceState(
        _.omit(this.queryParams(window.location.search), key),
        document.title,
        this.removeQueryParam(window.location.search, key)
      );
    },
    replaceUrlState: function(key, value) {
      history.replaceState(
        _.extend(this.queryParams(window.location.search), {key: value}),
        document.title,
        this.setQueryParam(window.location.search, key, value)
      );
    }
  }
};

let errorMixin = {
  data: function() {
    return {
      errored: false,
    };
  },
  methods: {
    showError: function(desc, exception) {
      this.errored = true;
      this.$emit("error", desc, exception);
    },
    clearError: function() {
      this.errored = false;
    }
  }
}

let twoPanelMixin = {
  data: function() {
    return {
      panelSize: null,
    }
  },
  methods: {
    setPanelSize: function (arbitrarySize) {
      this.panelSize = arbitrarySize;
    }
  }
};

let validatorMixin = {
  props: {
    datasetId: String,
    fileStage: String,
    api: Object,
  },
  data: function() {
    return {
      validating: {},
      validationRunning: false,
      validationResults: {},
      validationLog: [],
    }
  },
  methods: {
    handleValidationResults: function(errs) {
      _.forEach(errs, item => {
        this.$set(this.validationResults, item.eTag, item.errors)
        this.$delete(this.validating, item.eTag);
      });
      if (_.isUndefined(_.find(errs, (err) => err.errors.length > 0))) {
        this.validationLog.push('<span class="text-success">No errors found ✓</span>');
      } else {
        errs.forEach(item => {
          if (item.errors.length > 0) {
            this.validationLog.push('<span class="text-danger">' + decodeURI(item.key) + ':</span>')
            item.errors.forEach(err => {
              this.validationLog.push("    " + err.line + "/" + err.pos + " - " + err.error);
            })
          }
        });
      }
    },
    validateFiles: function (tagToKey) {
      this.tab = 'validation';
      this.validationRunning = true;
      this.validationLog = [];
      let allTags = _.isEmpty(tagToKey) ? this.files.map(f => f.eTag) : _.keys(tagToKey);
      _.forEach(allTags, tag => this.$set(this.validating, tag, true));

      this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
        .then(errs => this.handleValidationResults(errs))
        .catch(error => this.showError("Error attempting validation", error))
        .finally(() => {
          this.validating = {};
          this.validationRunning = false;
        });
    },
  }
};

let previewPanelMixin = {
  mixins: [errorMixin],
  props: {
    datasetId: String,
    fileStage: String,
    panelSize: Number,
    previewing: Object,
    config: Object,
    api: Object,
    maxSize: Number,
    validationResults: {
      type: Object,
      default: function () { return {} },
    },
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
      wrap: false,
      prettifying: false,
      prettified: false,
      showingError: false,
      worker: null,
      errored: false,
      errors: null,
      lineHandles: [],
      firstLoad: true,
    }
  },
  methods: {
    prettifyXml: function(xml) {
      let parser = new DOMParser();
      let xmlDoc = parser.parseFromString(xml, 'application/xml');
      let xsltDoc = parser.parseFromString(`
        <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
          <xsl:template match="node()|@*">
            <xsl:copy>
              <xsl:apply-templates select="node()|@*"/>
            </xsl:copy>
          </xsl:template>
          <xsl:output indent="yes"/>
        </xsl:stylesheet>
      `, 'application/xml');

      let xsltProcessor = new XSLTProcessor();
      xsltProcessor.importStylesheet(xsltDoc);
      let resultDoc = xsltProcessor.transformToDocument(xmlDoc);
      return new XMLSerializer().serializeToString(resultDoc);
    },
    makePretty: function() {
      this.prettifying = true;
      this.previewData = this.prettifyXml(this.previewData);
      Vue.nextTick(() => {
        this.prettified = true
        this.prettifying = false;
      });
    },
    validate: function () {
      if (this.previewing === null) {
        return;
      }

      if (this.validationResults[this.previewing.eTag]) {
        this.errors = this.validationResults[this.previewing.eTag];
        this.updateErrors();
      } else {
        this.validating = true;
        let tagToKey = _.fromPairs([[this.previewing.eTag, this.previewing.key]]);
        this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
          .then(errors => {
            let e = _.find(errors, e => this.previewing.eTag === e.eTag);
            if (e) {
              this.errors = e.errors;
              this.updateErrors()
              this.$emit("validation-results", this.previewing.eTag, e.errors);
            }
          })
          .catch(error => this.showError("Error attempting validation", error))
          .finally(() => this.validating = false);
      }
    },
    updateErrors: function () {
      function makeWidget(err) {
        let widget = document.createElement("div");
        widget.style.color = "#822";
        widget.style.backgroundColor = "rgba(255,197,199,0.44)";
        widget.innerHTML = err.error;
        return widget;
      }

      function makeMarker(doc, err) {
        let marker = document.createElement("div");
        marker.style.color = "#822";
        marker.style.marginLeft = "3px";
        marker.className = "validation-error";
        marker.innerHTML = '<i class="fa fa-exclamation-circle"></i>';
        marker.querySelector("i").setAttribute("title", err.error);
        marker.addEventListener("click", function () {
          if (marker.widget) {
            marker.widget.clear();
            delete marker.widget;
          } else {
            marker.widget = doc.addLineWidget(err.line - 1, makeWidget(err));
          }
        });
        return marker;
      }

      // First clear any existing errors on the document...
      if (this.previewing && this.editor) {
        let doc = this.editor.getDoc();
        this.lineHandles.forEach(handle => doc.removeLineClass(handle, 'background'));
        doc.clearGutter('validation-errors');
        this.lineHandles = [];

        if (this.errors) {
          this.errors.forEach(e => {
            this.lineHandles.push(doc.addLineClass(e.line - 1, 'background', 'line-error'));
            doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(doc, e));
          });
        }
      }
    },
    setLoading:  function() {
      this.loading = true;
      this.$emit("loading");
    },
    load: function () {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.api.fileUrls(this.datasetId, this.fileStage, [this.previewing.key])
        .then(data => this.worker.postMessage({
          type: 'preview',
          url: data[this.previewing.key],
          max: this.config.maxPreviewSize,
        }))
        .catch(error => this.showError('Unable to load preview URL', error))
        .finally(() => this.loading = false);
    },
    refresh: function() {
      this.editor.refresh();
    },
    receiveMessage: function(msg) {
      if (msg.data.error) {
        let errObj = msg.data.error;
        this.previewData = errObj.line
          ? "Error at line " + errObj.line + ": " + errObj.error
          : "Error: " + msg.data.error.error;
        this.editor.setOption("mode", null);
        this.loading = false;
        this.showingError = true;
      } else if (msg.data.init) {
        if (this.editor) {
          this.validate();
          this.editor.setOption("mode", "xml");

          // On the first load of a given file scroll back
          // to the beginning...
          if (this.firstLoad) {
            this.editor.scrollTo(0, 0);
            this.firstLoad = false;
          }
        }
        // Stop loading indicator when first data arrives
        this.loading = false;
        this.showingError = false;
        this.previewTruncated = false;
        this.previewData = msg.data.text;
      } else {
        this.previewData += msg.data.text;
        this.previewTruncated = msg.data.truncated;
      }
      if (msg.data.done) {
        this.$emit("loaded");
      }
    }
  },
  watch: {
    previewData: function (newValue, oldValue) {
      let editorValue = this.editor.getValue();
      if (newValue !== editorValue) {
        let scrollInfo = this.editor.getScrollInfo();
        this.editor.setValue(newValue);
        this.editor.scrollTo(scrollInfo.left, scrollInfo.top);
      }
      // FIXME: why is this only needed some times, e.g for convert
      // previews but not file previews?
      if (newValue !== oldValue) {
        if (!this.prettifying) {
          this.prettified = false;
        }
        this.refresh();
      }
    },
    previewing: function (newValue, oldValue) {
      if (!_.isEqual(newValue, oldValue)) {
        this.load();
      }
      if (newValue && oldValue && newValue.key !== oldValue.key) {
        this.firstLoad = true;
      }
    },
    panelSize: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.refresh();
      }
    },
  },
  created: function() {
    this.worker = new Worker(this.config.previewLoader);
    this.worker.onmessage = this.receiveMessage;
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineWrapping: this.wrap,
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", () => this.updateErrors());

    this.load();
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
    if (this.worker) {
      this.worker.terminate();
    }
  },
  template: `
    <div class="preview-container">
      <textarea>{{previewData}}</textarea>
      <div class="validation-loading-indicator" v-if="validating">
        <i class="fa fa-circle"></i>
      </div>
      <div class="valid-indicator" title="No errors detected"
           v-if="!validating && previewing && (_.isArray(errors) && errors.length === 0)">
        <i class="fa fa-check"></i>
      </div>
      <div class="preview-loading-indicator" v-if="loading">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
      <button v-else-if="!showingError"
              v-on:click="makePretty"
              v-bind:class="{'active': !prettified}"
              v-bind:disabled="previewTruncated || prettified"
              class="pretty-xml btn btn-sm"    
              title="Apply code formatting...">
        <i class="fa fa-code"></i>
      </button>
    </div>
  `
};

Vue.component("preview", {
  mixins: [previewPanelMixin]
});

Vue.component("convert-preview", {
  mixins: [previewPanelMixin],
  props: {
    mappings: Array,
    trigger: String,
    api: Object,
  },
  methods: {
    validate: function() {
      // FIXME: not yet supported
    },
    load: function() {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.worker.postMessage({
        type: 'convert-preview',
        url: this.api.convertFileUrl(this.datasetId, this.fileStage, this.previewing.key),
        mappings: this.mappings,
        max: this.config.maxPreviewSize,
      });
    }
  },
  watch: {
    trigger: function() {
      this.load();
    },
    config: function(newConfig, oldConfig) {
      if (newConfig !== oldConfig && newConfig !== null) {
        console.log("Refresh convert preview...");
        this.load();
      }
    }
  }
});

Vue.component("modal-alert", {
  props: {
    title: String,
    cls: {
      type: String,
      default: 'danger'
    },
    accept: {
      type: String,
      default: "Okay"
    },
    cancel: {
      type: String,
      default: "Cancel"
    }
  },
  mounted() {
    this.$el.querySelector("button.close").focus();
  },
  template: `
    <div v-bind:class="cls" class="modal modal-alert" tabindex="-1" role="dialog">
      <div class="modal-dialog modal-sm" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{title}}</h5>
            <button type="button" class="close" aria-label="Close" v-on:click="$emit('close')" tabindex="-1">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <slot></slot>
          </div>
          <div class="modal-footer">
            <button v-if="cancel" type="button" class="btn" v-on:click="$emit('close')" autofocus>{{cancel}}</button>
            <button v-if="accept" type="button" class="btn" v-bind:class="'btn-' + cls" v-on:click="$emit('accept')">{{accept}}</button>
          </div>
        </div>
      </div>
    </div>
  `
});





let initialStageState = function() {
  return {
    loaded: false,
    loadingMore: false,
    truncated: false,
    tab: 'preview',
    previewing: null,
    deleting: {},
    downloading: {},
    loadingInfo: {},
    selected: {},
    filter: {
      value: "",
      active: false
    },
    files: [],
    log: [],
    fileInfo: null,
  };
};

let stageMixin = {
  props: {
    datasetId: String,
    active: Boolean,
  },
  data: function() {
    return initialStageState();
  },
  computed: {
    selectedKeys: function () {
      return Object.keys(this.selected);
    },
    selectedTags: function() {
      return _.fromPairs(Object.values(this.selected).map(f => [f.eTag, f.key]));
    }
  },
  methods: {
    reset: function() {
      Object.assign(this.$data, initialStageState());
    },
    clearFilter: function () {
      this.filter.value = "";
      return this.refresh();
    },
    filterFiles: function () {
      let func = () => {
        this.filter.active = true;
        return this.load().then(r => {
          this.filter.active = false;
          return r;
        });
      };
      return _.debounce(func, 300)();
    },
    refresh: _.debounce(function() {
      return this.load();
    }, 500),
    load: function () {
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value)
        .then(data => {
          this.files = data.files;
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loaded = true);
    },
    loadMore: function () {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key
        : null;
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value, from)
        .then(data => {
          this.files.push.apply(this.files, data.files);
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loadingMore = false);
    },
    downloadFiles: function(keys) {
      keys.forEach(key => this.$set(this.downloading, key, true));
      this.api.fileUrls(this.datasetId, this.fileStage, keys)
        .then(urls => {
          _.forIn(urls, (url, fileName) => {
            window.open(url, '_blank');
            this.$delete(this.downloading, fileName);
          });
        })
        .catch(error => this.showError("Error fetching download URLs", error))
        .finally(() => this.downloading = {});
    },
    deleteFiles: function (keys) {
      if (_.isEmpty(keys) || keys.includes(this.previewing)) {
        this.previewing = null;
      }
      let dkeys = _.isEmpty(keys) ? this.files.map(f => f.key) : keys;
      dkeys.forEach(key => this.$set(this.deleting, key, true));
      this.api.deleteFiles(this.datasetId, this.fileStage, keys)
        .then(() => {
          dkeys.forEach(key => {
            this.$delete(this.deleting, key);
            this.$delete(this.selected, key);
          });
          this.refresh();
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    info: function(key) {
      this.$set(this.loadingInfo, key, true);
      return this.api.info(this.datasetId, this.fileStage, key)
        .then(r => this.fileInfo = r)
        .catch(error => this.showError("Error fetching file info", error))
        .finally(() => this.loadingInfo = {});
    },
    selectItem: function(file) {
      this.$set(this.selected, file.key, file);
    },
    deselectItem: function(file) {
      this.$delete(this.selected, file.key);
    },
    showError: function() {}, // Overridden by inheritors
    deselect: function() {
      this.previewing = null;
    }
  },
  watch: {
    active: function(newValue) {
      if (newValue) {
        this.load();
      }
    },
    datasetId: function() {
      this.reset();
      this.load();
    }
  },
  created: function() {
    this.load();
  },
};
