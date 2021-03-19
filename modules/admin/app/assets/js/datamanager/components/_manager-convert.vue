<script lang="ts">

import Draggable from 'vuedraggable';
import FilePicker from './_file-picker';
import ModalConvertConfig from './_modal-convert-config';
import TransformationEditor from './_transformation-editor';
import TransformationItem from './_transformation-item';
import PanelLogWindow from './_panel-log-window';
import DragHandle from './_drag-handle';
import PanelConvertPreview from './_panel-convert-preview';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinError from './_mixin-error';
import MixinUtil from './_mixin-util';
import DataManagerApi from '../api';

import _partition from 'lodash/partition';
import _takeWhile from 'lodash/takeWhile';
import _isEqual from 'lodash/isEqual';
import _find from 'lodash/find';


let initialConvertState = function(config) {
  return {
    convertJobId: null,
    ingesting: {},
    previewStage: config.input,
    previewPipeline: [],
    previewing: null,
    tab: 'preview',
    log: [],
    showOptions: false,
    available: [],
    enabled: [],
    mappings: [], // IDs of enabled transformations
    editing: null,
    loading: false,
  };
};

export default {
  components: {Draggable, FilePicker, ModalConvertConfig, PanelConvertPreview, TransformationEditor, TransformationItem, PanelLogWindow, DragHandle},
  mixins: [MixinTwoPanel, MixinValidator, MixinError, MixinUtil],
  props: {
    datasetId: String,
    fileStage: String,
    config: Object,
    api: DataManagerApi,
    active: Boolean,
  },
  data: function () {
    return initialConvertState(this.config);
  },
  methods: {
    loadTransformations: function() {
      this.loading = true;

      return this.api.listDataTransformations()
          .then(available => {
            let each = _partition(available, item => !this.mappings.includes(item.id));
            this.available = each[0];
            this.enabled = this.mappings.map(id => _find(each[1], a => a.id === id));
          })
          .catch(error => this.showError("Unable to load transformations", error))
          .finally(() => this.loading = false);
    },
    editTransformation: function(item, withPreviewPipeline) {
      this.editing = item;
      // if editing in the context of a pipeline, set the preview
      // pipeline to be the items ahead of this one in the enabled
      // list...
      this.previewPipeline = withPreviewPipeline
          ? _takeWhile(this.enabled, dt => dt.id !== item.id).map(dt => [dt.bodyType, dt.body])
          : [];
    },
    newTransformation: function() {
      this.editing = {
        id: null,
        repoId: this.config.repoId,
        name: "",
        bodyType: "xslt",
        body: "",
        comments: "",
      };
    },
    closeEditForm: function() {
      this.editing = null;
      this.loadTransformations();
    },
    saved: function(item) {
      this.editing = item;
    },
    convert: function(file, force) {
      console.debug("Converting:", file)
      this.api.convert(this.datasetId, file ? file.key : null, {mappings: this.mappings, force: force})
          .then(data => {
            this.convertJobId = data.jobId;
            this.monitorConvert(data.url, data.jobId);
          })
          .catch(error => this.showError("Error submitting conversion", error));
    },
    cancelConvert: function() {
      if (this.convertJobId) {
        this.api.cancelConvert(this.convertJobId).then(r => {
          if (r.ok) {
            this.convertJobId = null;
          }
        });
      }
    },
    monitorConvert: function (url, jobId) {
      this.tab = 'convert';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          this.log.push(msg.data.msg);
          this.$emit('refresh-stage', this.config.output);
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

          this.convertJobId = null;
          this.removeUrlState('convert-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DataManagerApi.DONE_MSG, ERR: DataManagerApi.ERR_MSG});
      this.replaceUrlState('convert-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "convert-job-id");
      if (jobId) {
        this.convertJobId = jobId;
        this.monitorConvert(this.config.monitorUrl(jobId), jobId);
      }
    },
    loadConfig: function(): Promise<void> {
      return this.api.getConvertConfig(this.datasetId)
          .then(data => this.mappings = data.map(item => item.id))
          .catch(error => this.showError("Error loading convert configuration", error));
    },
    saveConfig: function() {
      let mappings = this.enabled.map(item => item.id);
      if (!_isEqual(mappings, this.mappings)) {
        console.log("saving enabled:", this.enabled)
        this.mappings = mappings;
        this.api.saveConvertConfig(this.datasetId, this.mappings)
            .catch(error => this.showError("Failed to save mapping list", error));
      }
    },
    priorConversions: function(dt) {
      return _takeWhile(this.enabled, s => s.id !== dt.id);
    }
  },
  watch: {
    enabled: function() {
      if (!this.loading) {
        this.saveConfig();
      }
    },
    datasetId: function() {
      Object.assign(this.$data, initialConvertState(this.config));
      this.loadConfig().then(_ => {
        this.loadTransformations();
      });
    }
  },
  created: function () {
    this.loadConfig().then(_ => {
      this.loadTransformations();
    });
    this.resumeMonitor();
  },
};
</script>

<template>
  <div id="manager-convert-container" class="stage-manager-container">

    <transformation-editor
        v-if="editing !== null"
        v-bind:id="editing.id"
        v-bind:name="editing.name"
        v-bind:generic="!editing.repoId"
        v-bind:body-type="editing.bodyType"
        v-bind:body="editing.body"
        v-bind:comments="editing.comments"
        v-bind:dataset-id="datasetId"
        v-bind:file-stage="previewStage"
        v-bind:init-previewing="previewing"
        v-bind:config="config"
        v-bind:api="api"
        v-bind:input-pipeline="previewPipeline"
        v-on:saved="saved"
        v-on:close="closeEditForm"/>

    <div class="actions-bar">
      <file-picker v-bind:disabled="convertJobId !== null"
                   v-bind:dataset-id="datasetId"
                   v-bind:file-stage="config.input"
                   v-bind:api="api"
                   v-bind:config="config"
                   v-bind:placeholder="'Select file to preview...'"
                   v-model="previewing" />

      <button class="btn btn-sm btn-default" v-on:click.prevent="newTransformation">
        <i class="fa fa-file-o"></i>
        New Transformation...
      </button>

      <button v-if="!convertJobId" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = true">
        <i class="fa fa-fw fa-file-code-o"/>
        Convert Files...
      </button>
      <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelConvert">
        <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Cancel Convert
      </button>
    </div>

    <div id="convert-panel-container" class="panel-container">
      <div class="top-panel">

        <modal-convert-config
            v-bind:show="showOptions"
            v-bind:config="config"
            v-bind:api="api"
            v-bind:dataset-id="datasetId"
            v-on:close="showOptions = false"
            v-on:convert="convert"
            v-show="showOptions" />

        <div id="convert-mappings">
          <div class="card">
            <h4 class="card-header">
              Available Transformations
            </h4>

            <div class="transformation-list-placeholder" v-if="enabled.length === 0 && available.length === 0">
              <h3>No transformations available.</h3>
              <p><a href="#" v-on:click.prevent="newTransformation">Create a new one now...</a></p>
            </div>
            <div class="transformation-list-placeholder" v-else-if="available.length === 0">
              <p>Drag transformations into this area to deactivate them.</p>
            </div>

            <draggable
                class="list-group transformation-list"
                draggable=".transformation-item"
                group="transformations"
                v-bind:sort="false"
                v-model="available">
              <transformation-item
                  v-for="(dt, i) in available"
                  v-bind:item="dt"
                  v-bind:key="i"
                  v-on:edit="editTransformation(dt, false)"
              />
            </draggable>
          </div>

          <div class="spacer"></div>
          <div class="card">
            <h4 class="card-header">
              Enabled Transformations
            </h4>

            <div class="transformation-list-placeholder" v-if="enabled.length === 0">
              <h3>No transformations are enabled.</h3>
              <p>Drag available transformations into this area to
                activate them.</p>
            </div>

            <draggable
                class="list-group transformation-list"
                draggable=".transformation-item"
                group="transformations"
                v-bind:sort="true"
                v-model="enabled">
              <transformation-item
                  v-for="(dt, i) in enabled"
                  v-bind:item="dt"
                  v-bind:key="i"
                  v-on:edit="editTransformation(dt, true)"
              />
            </draggable>
          </div>
        </div>
      </div>

      <div id="convert-status-panels" class="bottom-panel">
        <ul class="status-panel-tabs nav nav-tabs">
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview
              <template v-if="previewing"> - {{previewing.key}}</template>
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'convert'}"
               v-on:click.prevent="tab = 'convert'">
              Convert Log
            </a>
          </li>
          <li>
            <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="() => $root.$el.querySelector('#convert-status-panels')"
                v-bind:container="() => $root.$el.querySelector('#convert-panel-container')"
                v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div class="status-panels">
          <div class="status-panel" v-show="tab === 'preview'">
            <panel-convert-preview
                v-bind:dataset-id="datasetId"
                v-bind:file-stage="previewStage"
                v-bind:mappings="mappings"
                v-bind:trigger="JSON.stringify({
                         mappings: mappings,
                         previewing: previewing
                       })"
                v-bind:previewing="previewing"
                v-bind:panel-size="panelSize"
                v-bind:config="config"
                v-bind:api="api"
                v-on:error="showError"
                v-show="previewing !== null"/>
            <div class="panel-placeholder" v-if="previewing === null">
              No file selected.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'convert'">
            <panel-log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Convert log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
