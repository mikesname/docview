<script>

import FilterControl from './_filter-control';
import ValidateButton from './_validate-button';
import DeleteButton from './_delete-button';
import FilesTable from './_files-table';
import DragHandle from './_drag-handle';
import ModalInfo from './_modal-info';
import LogWindow from './_log-window';
import IngestOptionsPanel from './_ingest-options-panel';
import PanelFilePreview from './_panel-file-preview';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinError from './_mixin-error';
import MixinPreview from './_mixin-preview';
import MixinStage from './_mixin-stage';
import MixinUtil from './_mixin-util';
import DAO from '../dao';

import _fromPairs from 'lodash/fromPairs';
import _startsWith from 'lodash/startsWith';
import _last from 'lodash/last';


export default {
  components: {FilterControl, FilesTable, LogWindow, DragHandle, ModalInfo, PanelFilePreview, ValidateButton, DeleteButton, IngestOptionsPanel},
  mixins: [MixinStage, MixinTwoPanel, MixinPreview, MixinValidator, MixinError, MixinUtil],
  props: {
    datasetId: String,
    fileStage: String,
    config: Object,
    api: DAO,
  },
  data: function () {
    return {
      waiting: false,
      ingestJobId: null,
      showOptions: false,
      propertyConfigs: [],
      opts: null,
    }
  },
  methods: {
    monitorIngest: function (url, jobId) {
      this.tab = 'ingest';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          if (this.log && _startsWith(_last(this.log), "Ingesting...") && _startsWith(msg.data.msg, "Ingesting...")) {
            this.log.splice(this.log.length - 1, 1, msg.data.msg);
          } else {
            this.log.push(msg.data.msg);
          }
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

          this.ingestJobId = null;
          this.removeUrlState('ingest-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DAO.DONE_MSG, ERR: DAO.ERR_MSG});
      this.replaceUrlState('ingest-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "ingest-job-id");
      if (jobId) {
        this.ingestJobId = jobId;
        this.monitorIngest(this.config.monitorUrl(jobId), jobId, this.files.map(f => f.key));
      }
    },
    doIngest: function(opts, commit) {
      this.waiting = true;

      // Save opts for the next time we open the config UI
      this.opts = opts;

      this.api.ingestFiles(this.datasetId, this.selectedKeys, opts, commit)
          .then(data => {
            if (data.url && data.jobId) {
              // Switch to ingest tab...
              this.tab = "ingest";
              // Clear existing log...
              this.log.length = 0;
              this.showOptions = false;

              this.ingestJobId = data.jobId;
              this.monitorIngest(data.url, data.jobId);
            } else {
              console.error("unexpected job data", data);
            }
          })
          .catch(error => this.showError("Error running ingest", error))
          .finally(() => this.waiting = false);
    },
    loadConfig: function() {
      this.api.getImportConfig(this.datasetId)
          .then(data => this.opts = data);
    },
    loadPropertyConfigs: function() {
      this.loading = true;
      this.api.listFiles(this.datasetId, this.config.config)
          .then(data => this.propertyConfigs = data.files.filter(f => f.key.endsWith(".properties")))
          .catch(e => this.showError("Error loading files", e))
          .finally(() => this.loading = false);
    },
  },
  created() {
    this.resumeMonitor();
    this.loadPropertyConfigs();
    this.loadConfig();
  },
};
</script>

<template>
  <div id="ingest-manager-container" class="stage-manager-container">
    <div class="actions-bar">
      <filter-control
          v-bind:filter="filter"
          v-on:filter="filterFiles"
          v-on:clear="clearFilter"
          v-on:refresh="load" />

      <validate-button
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || ingestJobId !== null"
          v-bind:active="validationRunning"
          v-on:validate="validateFiles(selectedTags)"
      />

      <delete-button
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || ingestJobId !== null"
          v-bind:active="deleting.length > 0"
          v-on:delete="deleteFiles(selectedKeys)"
      />

      <button v-bind:disabled="files.length === 0 || ingestJobId" class="btn btn-sm btn-default"
              v-on:click.prevent="showOptions = !showOptions" v-if="selectedKeys.length">
        <i v-if="!ingestJobId" class="fa fa-fw fa-database"/>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Ingest Selected... ({{selectedKeys.length}})
      </button>
      <button v-bind:disabled="files.length===0 || ingestJobId" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = !showOptions"
              v-else>
        <i v-if="!ingestJobId" class="fa fa-fw fa-database"/>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Ingest All...
      </button>

      <ingest-options-panel
          v-if="showOptions"
          v-bind:waiting="waiting"
          v-bind:props="propertyConfigs"
          v-bind:opts="opts"
          v-bind:api="api"
          v-bind:config="config"
          v-bind:dataset-id="datasetId"
          v-on:saving="waiting = true"
          v-on:saved-config="doIngest"
          v-on:update="loadPropertyConfigs"
          v-on:close="showOptions = false" />

      <modal-info v-if="fileInfo !== null" v-bind:file-info="fileInfo" v-on:close="fileInfo = null"/>
    </div>

    <div id="ingest-panel-container" class="panel-container">
      <div class="top-panel">
        <files-table
            v-bind:api="api"
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:loading-more="loadingMore"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:downloading="downloading"
            v-bind:loading-info="loadingInfo"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:validate-files="validateFiles"
            v-on:load-more="loadMore"
            v-on:show-preview="showPreview"
            v-on:item-selected="selectItem"
            v-on:item-deselected="deselectItem"
            v-on:deselect-all="deselect"
            v-on:info="info"
        />
      </div>

      <div id="ingest-status-panel" class="bottom-panel">
        <ul class="status-panel-tabs nav nav-tabs">
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview
              <template v-if="previewing"> - {{previewing.key|decodeURI}}</template>
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
               v-on:click.prevent="tab = 'validation'">
              Validation Log
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
               v-on:click.prevent="tab = 'ingest'">
              Ingest Log
            </a>
          </li>
          <li>
            <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="$root.$el.querySelector('#ingest-status-panel')"
                v-bind:container="$root.$el.querySelector('#ingest-panel-container')"
                v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div class="status-panels">
          <div class="status-panel" v-show="tab === 'preview'">
            <panel-file-preview v-bind:dataset-id="datasetId"
                     v-bind:file-stage="fileStage"
                     v-bind:previewing="previewing"
                     v-bind:panel-size="panelSize"
                     v-bind:config="config"
                     v-bind:api="api"
                     v-bind:validation-results="validationResults"
                     v-on:validation-results="(tag, e) => this.$set(this.validationResults, tag, e)"
                     v-on:error="showError"
                     v-show="previewing !== null"/>
            <div class="panel-placeholder" v-if="previewing === null">
              No file selected.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'validation'">
            <log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
            <div id="validation-placeholder" class="panel-placeholder" v-else>
              Validation log output will show here.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'ingest'">
            <log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Ingest log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

