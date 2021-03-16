<script>

import FilterControl from './_filter-control';
import ButtonValidate from './_button-validate';
import ButtonDelete from './_button-delete';
import FilesTable from './_files-table';
import DragHandle from './_drag-handle';
import PanelFilePreview from './_panel-file-preview';
import ModalInfo from './_modal-info';
import PanelLogWindow from './_panel-log-window';
import ModalRsConfig from './_modal-rs-config';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinPreview from './_mixin-preview';
import MixinError from './_mixin-error';
import MixinStage from './_mixin-stage';
import MixinUtil from './_mixin-util';

import DataManagerApi from '../api';


export default {
  components: {FilterControl, FilesTable, PanelLogWindow, DragHandle, ModalInfo, PanelFilePreview, ButtonValidate, ButtonDelete, ModalRsConfig},
  mixins: [MixinStage, MixinTwoPanel, MixinPreview, MixinValidator, MixinError, MixinUtil],
  props: {
    fileStage: String,
    config: Object,
    api: DataManagerApi,
  },
  data: function () {
    return {
      syncJobId: null,
      showOptions: false,
      syncConfig: null,
      waiting: false,
    }
  },
  methods: {
    doSync: function (opts) {
      this.waiting = true;

      this.syncConfig = opts;

      this.api.sync(this.datasetId, opts)
          .then(data => {
            this.showOptions = false;
            this.syncJobId = data.jobId;
            this.monitorSync(data.url, data.jobId);
          })
          .catch(error => this.showError("Error running sync", error))
          .finally(() => this.waiting = false);
    },
    cancelSync: function () {
      if (this.syncJobId) {
        this.api.cancelSync(this.syncJobId).then(r => {
          if (r.ok) {
            this.syncJobId = null;
          }
        });
      }
    },
    monitorSync: function (url, jobId) {
      this.tab = 'sync';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          this.log.push(msg.data.msg);
          this.refresh();
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

          this.syncJobId = null;
          this.removeUrlState('sync-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DataManagerApi.DONE_MSG, ERR: DataManagerApi.ERR_MSG});
      this.replaceUrlState('sync-job-id', jobId);
    },
    resumeMonitor: function () {
      let jobId = this.getQueryParam(window.location.search, "sync-job-id");
      if (jobId) {
        this.syncJobId = jobId;
        this.monitorSync(this.config.monitorUrl(jobId), jobId);
      }
    },
    loadConfig: function () {
      this.api.getSyncConfig(this.datasetId)
          .then(data => {
            this.syncConfig = data;
            console.debug("Loaded sync config", data);
          });
    },
  },
  created: function () {
    this.loadConfig();
    this.resumeMonitor();
  }
}
</script>
<template>
  <div id="rs-manager-container" class="stage-manager-container">
    <div class="actions-bar">
      <filter-control v-bind:filter="filter"
                      v-on:filter="filterFiles"
                      v-on:clear="clearFilter"/>

      <button-validate
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || syncJobId !== null"
          v-bind:active="validationRunning"
          v-on:validate="selectedKeys.length ? validateFiles(selectedKeys) : validateAll()"
      />

      <button-delete
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || syncJobId !== null"
          v-bind:active="deleting.length > 0"
          v-on:delete="deleteFiles(selectedKeys)"
      />

      <button v-if="!syncJobId" class="btn btn-sm btn-default"
              v-on:click.prevent="showOptions = !showOptions">
        <i class="fa fa-fw fa-clone"/>
        Sync Files...
      </button>
      <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelSync">
        <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Cancel Sync
      </button>

      <modal-rs-config
          v-if="showOptions"
          v-bind:waiting="waiting"
          v-bind:dataset-id="datasetId"
          v-bind:config="syncConfig"
          v-bind:api="api"
          v-on:saving="waiting = true"
          v-on:saved-config="doSync"
          v-on:error="showError"
          v-on:close="showOptions = false"/>

      <modal-info v-if="fileInfo !== null" v-bind:file-info="fileInfo" v-on:close="fileInfo = null"/>
    </div>

    <div id="rs-panel-container" class="panel-container">
      <div class="top-panel">
        <files-table
            v-bind:fileStage="fileStage"
            v-bind:loading-more="loadingMore"
            v-bind:loaded="loaded"
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
            v-on:toggle-all="toggleAll"
            v-on:toggle-file="toggleFile"
            v-on:info="info"
        />
      </div>

      <div id="rs-status-panels" class="bottom-panel">
        <ul class="status-panel-tabs nav nav-tabs">
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview
              <template v-if="previewing"> - {{ decodeURI(previewing.key) }}</template>
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
               v-on:click.prevent="tab = 'validation'">
              Validation Log
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'sync'}"
               v-on:click.prevent="tab = 'sync'">
              Sync Log
            </a>
          </li>
          <li>
            <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="() => $root.$el.querySelector('#rs-status-panels')"
                v-bind:container="() => $root.$el.querySelector('#oaipmh-panel-container')"
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
            <panel-log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
            <div  class="panel-placeholder" v-else>
              Validation log output will show here.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'sync'">
            <panel-log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Sync log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
