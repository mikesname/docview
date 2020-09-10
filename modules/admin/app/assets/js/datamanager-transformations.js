"use strict";

Vue.component("xslt-editor", {
  props: {
    value: String,
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: false,
      readOnly: false,
    });
    this.editor.on("change", () => {
      this.$emit('input', this.editor.getValue());
    });
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
  template: `
    <div class="xslt-editor">
      <textarea>{{value}}</textarea>
    </div>
  `
});

Vue.component("xquery-editor", {
  props: {
    value: String,
  },
  data: function() {
    return {
      mappings: this.deserialize(this.value),
      selected: -1,
    }
  },
  methods: {
    update: function() {
      this.$emit('input', this.serialize(this.mappings));
    },
    add: function() {
      this.mappings.push(["", "", "", ""]);
      this.selected = this.mappings.length - 1;
      this.update();
      let elem = this.$refs[this.selected + '-0'];
      if (elem) {
        elem.focus();
      }
    },
    duplicate: function(i) {
      let m = _.clone(this.mappings[i]);
      this.mappings.splice(i + 1, 0, m);
      this.update();
    },
    remove: function(i) {
      this.mappings.splice(i, 1);
      this.selected = i - 1;
      this.update();
    },
    moveUp: function(i) {
      if (i > 0) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i - 1, 0, m);
        this.selected = i - 1;
        this.update();
      }
    },
    moveDown: function(i) {
      if (i < this.mappings.length - 1) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i + 1, 0, m);
        this.selected = i + 1;
        this.update();
      }
    },
    deserialize: function(str) {
      if (str !== "") {
        // Ignore the header row here...
        return str
          .split("\n")
          .slice(1)
          .map (m => {
            let parts = m.split("\t");
            return [
              parts[0] ? parts[0] : "",
              parts[1] ? parts[1] : "",
              parts[2] ? parts[2] : "",
              parts[3] ? parts[3] : "",
            ];
          });
      } else {
        return [];
      }
    },
    serialize: function(mappings) {
      let header = ["target-path\ttarget-node\tsource-node\tvalue"]
      let rows = mappings.map(m => m.join("\t"))
      let all = _.concat(header, rows)
      return all.join("\n");
    },
  },
  template: `
    <div class="xquery-editor">
      <div class="xquery-editor-data">
        <div class="xquery-editor-header">
            <input readonly disabled type="text" value="target-path" @click="selected = -1"/>
            <input readonly disabled type="text" value="target-node" @click="selected = -1"/>
            <input readonly disabled type="text" value="source-node" @click="selected = -1"/>
            <input readonly disabled type="text" value="value" @click="selected = -1"/>
        </div>
        <div class="xquery-editor-mappings">
          <template v-for="(mapping, row) in mappings">
            <input
              v-for="col in [0, 1, 2, 3]"
              type="text"
              v-bind:ref="row + '-' + col"
              v-bind:key="row + '-' + col"
              v-bind:class="{'selected': selected === row}"
              v-model="mappings[row][col]"
              @change="update"
              @focusin="selected = row" />
          </template>
        </div>
      </div>
      <div class="xquery-editor-toolbar">
        <button class="btn btn-default btn-sm" v-on:click="add">
          <i class="fa fa-plus"></i>
          Add Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="duplicate(selected)">
          <i class="fa fa-copy"></i>
          Duplicate Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="remove(selected)">
          <i class="fa fa-trash-o"></i>
          Delete Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === 0" v-on:click="moveUp(selected)">
          <i class="fa fa-caret-up"></i>
          Move Up
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === mappings.length - 1" v-on:click="moveDown(selected)">
          <i class="fa fa-caret-down"></i>
          Move Down
        </button>
        <div class="xquery-editor-toolbar-info">
          Data mappings: {{mappings.length}}
        </div>
      </div>
    </div>
  `
});

Vue.component("edit-form-panes", {
  mixins: [twoPanelMixin],
  props: {
    id: Number,
    name: String,
    generic: Boolean,
    bodyType: String,
    body: String,
    comments: String,
    preview: Object,
    previewList: Array,
    config: Object,
  },
  data: function() {
    return {
      saving: false,
      previewing: this.preview,
      loading: false,
      panelSize: 0,
      data: {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
      },
      timestamp: (new Date()).toString(),
      fileStage: 'upload',
      inputValidationResults: [],
      outputValidationResults: [],
      showOptions: false,
      loadingIn: false,
      loadingOut: false,
    }
  },
  methods: {
    save: function() {
      this.saving = true;
      let p = this.id
        ? DAO.updateDataTransformation(this.id, this.data.generic, this.data)
        : DAO.createDataTransformation(this.data.generic, this.data);

      return p.then(item => {
        this.saving = false;
        this.$emit('saved', item)
      });
    },
    remove: function () {
      DAO.deleteDataTransformation(this.id).then(_ => {
        this.$emit('deleted');
        this.$emit('close');
      });
    },
  },
  computed: {
    mappings: function() {
      return [[this.data.bodyType, this.data.body]];
    },
    modified: function() {
      return !_.isEqual(this.data, {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
      });
    },
    valid: function() {
      return this.data.name.trim() !== "" && this.data.comments.trim() !== "";
    }
  },
  template: `
    <div class="modal" id="edit-form-modal">
      <div class="modal-dialog" id="edit-form-container">
        <div id="edit-form" class="modal-content">
          <div id="edit-form-heading" class="modal-header">
            <h5 class="modal-title">{{id ? ('Edit transformation: ' + name) : 'New Transformation...'}}</h5>
            <div class="close" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </div>
          </div>
          <div id="edit-form-panes" class="panel-container modal-body">
            <div id="edit-form-map" class="top-panel">
              <div id="edit-form-controls" class="controls">
                <label for="transformation-name">Name</label>
                <input v-model.trim="data.name" id="transformation-name" minlength="3" required/>
                <label for="transformation-type">Type</label>
                <select id="transformation-type" v-model="data.bodyType">
                  <option v-bind:value="'xquery'">XQuery</option>
                  <option v-bind:value="'xslt'">XSLT</option>
                </select>
                <label for="transformation-type">Scope</label>
                <select id="transformation-type" v-model="data.generic">
                  <option v-bind:value="false">Repository Specific</option>
                  <option v-bind:value="true">Generic</option>
                </select>
                <label for="transformation-comments">Description</label>
                <input v-model.trim="data.comments" id="transformation-comments" minlength="3" required />
                <div class="buttons">
                  <button class="btn btn-success btn-sm" v-on:click="save" v-bind:disabled="!valid || !modified">
                    Save
                    <i v-if="saving" class="fa fa-spin fa-circle-o-notch fa-fw"></i>
                    <i v-else class="fa fa-save fa-fw"></i>
                  </button>
                  <div class="dropdown">
                    <button class="btn btn-default btn-sm" v-on:click="showOptions = !showOptions">
                      <i class="fa fa-fw fa-ellipsis-v"></i>
                    </button>
                    <div v-if="showOptions" class="dropdown-backdrop" v-on:click="showOptions = false">
                    </div>
                    <div v-if="showOptions" class="dropdown-menu dropdown-menu-right show">
                      <button class="dropdown-item btn btn-sm" v-on:click="remove" v-bind:disabled="!Boolean(id)">Delete Transformation</button>
                    </div>
                  </div>
                </div>
              </div>
              <div id="edit-form-map-input">
                <xquery-editor v-if="data.bodyType === 'xquery'" v-model.lazy="data.body"/>
                <xslt-editor v-else v-model.lazy="data.body"></xslt-editor>
              </div>
            </div>
            <div id="edit-form-preview-section" class="bottom-panel">
              <div id="edit-form-preview-select">
                <label for="edit-form-preview-options">Preview transformation</label>
                <select id="edit-form-preview-options" v-model="previewing">
                  <option v-bind:value="null">---</option>
                  <option v-for="file in previewList" v-bind:value="file">{{file.key}}</option>
                </select>
                <button id="edit-form-preview-refresh"  title="Refresh preview"
                        class="btn btn-sm" v-bind:disabled="previewing === null || loadingOut" v-on:click="timestamp = (new Date()).toString()">
                  <i class="fa fa-refresh"></i>
                </button>
                <drag-handle v-bind:ns="'edit-form-preview-drag'"
                             v-bind:p2="$root.$el.querySelector('#edit-form-preview-section')"
                             v-bind:container="$root.$el.querySelector('#edit-form-panes')"
                              v-on:resize="setPanelSize" />
              </div>
              <div id="edit-form-previews">
                <div class="edit-form-preview-window">
                  <preview 
                    v-if="previewing !== null"
                    v-bind:file-stage="fileStage"
                    v-bind:previewing="previewing"
                    v-bind:errors="inputValidationResults"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-on:loading="loadingIn = true"
                    v-on:loaded="loadingIn = false" />
                  <div class="panel-placeholder" v-if="previewing === null">
                    Input preview
                  </div>
                </div>
                <div class="edit-form-preview-window">
                  <convert-preview 
                    v-if="previewing !== null"
                    v-bind:mappings="mappings"
                    v-bind:trigger="timestamp"
                    v-bind:file-stage="fileStage"
                    v-bind:previewing="previewing"
                    v-bind:errors="outputValidationResults"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-on:loading="loadingOut = true"
                    v-on:loaded="loadingOut = false" />
                  <div class="panel-placeholder" v-if="previewing === null">
                    Output preview
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
});
