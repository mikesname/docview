<script>

import _ from 'lodash';
import FilterControl from './filter-control';
import ValidateButton from './validate-button';
import DeleteButton from './delete-button';
import FilesTable from './files-table';
import DragHandle from './drag-handle';
import InfoModal from './info-modal';
import LogWindow from './log-window';
import RsConfigModal from './rs-config-modal';

export default {
  components: {FilterControl, FilesTable, LogWindow, DragHandle, InfoModal, ValidateButton, DeleteButton, RsConfigModal},
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    fileStage: String,
    config: Object,
    api: DAO,
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
      worker.postMessage({type: 'websocket', url: url, DONE: DONE_MSG, ERR: ERR_MSG});
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
            console.log("Loaded", data);
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

      <validate-button
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || syncJobId !== null"
          v-bind:active="validationRunning"
          v-on:validate="selectedKeys.length ? validateFiles(selectedKeys) : validateAll()"
      />

      <delete-button
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || syncJobId !== null"
          v-bind:active="!_.isEmpty(deleting)"
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

      <rs-config-modal
          v-if="showOptions"
          v-bind:waiting="waiting"
          v-bind:dataset-id="datasetId"
          v-bind:config="syncConfig"
          v-bind:api="api"
          v-on:saving="waiting = true"
          v-on:saved-config="doSync"
          v-on:error="showError"
          v-on:close="showOptions = false"/>

      <info-modal v-if="fileInfo !== null" v-bind:file-info="fileInfo" v-on:close="fileInfo = null"/>
    </div>

    <div id="rs-panel-container" class="panel-container">
      <div class="top-panel">
        <files-table
            v-bind:api="api"
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
            v-on:info="info"
        />
      </div>

      <div id="rs-status-panels" class="bottom-panel">
        <ul class="status-panel-tabs nav nav-tabs">
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview
              <template v-if="previewing"> - {{previewing.key|decodeUri}}</template>
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
                v-bind:p2="$root.$el.querySelector('#rs-status-panels')"
                v-bind:container="$root.$el.querySelector('#oaipmh-panel-container')"
                v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div class="status-panels">
          <div class="status-panel" v-show="tab === 'preview'">
            <preview v-bind:dataset-id="datasetId"
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
            <div  class="panel-placeholder" v-else>
              Validation log output will show here.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'sync'">
            <log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Sync log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
