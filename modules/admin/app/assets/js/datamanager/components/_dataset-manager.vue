<script lang="ts">

import ModalDatasetConfig from './_modal-dataset-config';
import ModalDatasetImport from './_modal-dataset-import'
import ManagerOaipmh from './_manager-oaipmh';
import ManagerUpload from './_manager-upload';
import ManagerIngest from './_manager-ingest';
import ManagerRs from './_manager-rs';
import ManagerConvert from './_manager-convert.vue';

import MixinUtil from './_mixin-util';
import {ImportDataset, ImportDatasetSrc} from '../types';
import {DatasetManagerApi} from "../api";

import _find from 'lodash/find';
import _merge from 'lodash/merge';
import _omit from 'lodash/omit';
import _modalDatasetImport from "./_modal-dataset-import.vue";

export default {
  components: {
    ModalDatasetConfig, ModalDatasetImport, ManagerOaipmh, ManagerUpload, ManagerIngest, ManagerRs, ManagerConvert},
  mixins: [MixinUtil],
  props: {
    config: Object,
    api: DatasetManagerApi,
    initTab: String,
  },
  data: function() {
    return {
      loaded: false,
      datasets: [],
      dataset: null,
      tab: this.initTab,
      error: null,
      showOptions: false,
      showDatasetForm: false,
      showImportForm: false,
      showSelector: false,
      stats: {},
    }
  },
  methods: {
    setError: function(err: string, exc?: Error) {
      this.error = err + (exc ? (": " + exc.message) : "");
    },
    switchTab: function(tab: string) {
      this.tab = tab;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'tab': tab}),
          document.title,
          this.setQueryParam(window.location.search, 'tab', tab));
    },
    selectDataset: function(ds?: ImportDataset) {
      if (!ds) {
        this.dataset = null;
        return;
      }
      this.dataset = ds;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'ds': ds.id}),
          document.title,
          this.setQueryParam(window.location.search, 'ds', ds.id));
    },
    closeDataset: function() {
      this.dataset = null;
      history.pushState(
          _omit(this.queryParams(window.location.search), 'ds', 'tab'),
          document.title,
          window.location.pathname
          + this.removeQueryParam(window.location.search, ['ds', 'tab']));
    },
    loadDatasets: function() {
      this.api.datasetStats().then(stats => this.stats = stats);
      return this.api.listDatasets()
          .then(dsl => this.datasets = dsl)
          .catch(e => this.showError("Error loading datasets", e))
          .finally(() => this.loaded = true);
    },
    reloadDatasets: function(ds?: ImportDataset) {
      this.loadDatasets().then(() => {
        if(ds) {
          this.selectDataset(ds);
        }
      });
    },
    stageName: function(code: ImportDatasetSrc): string {
      switch (code) {
        case "oaipmh": return "Harvesting";
        case "upload": return "Uploads";
        case "rs": return "ResourceSync";
        default: return code;
      }
    }
  },
  created() {
    if (!this.config.versioned) {
      this.error = "Note: file storage does not have versioning enabled."
    }
    window.onpopstate = event => {
      if (event.state && event.state.tab) {
        this.tab = event.state.tab;
      } else {
        this.tab = this.initTab;
      }
      if (event.state && event.state.ds) {
        this.dataset = _find(this.datasets, d => d.id === event.state.ds);
      } else {
        this.dataset = null;
      }
    };

    let qsTab = this.getQueryParam(window.location.search, "tab");
    if (qsTab) {
      this.tab = qsTab;
    }

    this.loadDatasets().then(() => {
      let qsDs = this.getQueryParam(window.location.search, "ds");
      if (qsDs) {
        this.selectDataset(_find(this.datasets, d => d.id === qsDs));
      }
    });
  },

};
</script>

<template>
  <div id="dataset-manager-container" class="container">
    <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
      <span class="close" v-on:click="error = null">&times;</span>
      {{error}}
    </div>
    <modal-dataset-config v-if="showDatasetForm"
                          v-bind:info="dataset"
                          v-bind:config="config"
                          v-bind:api="api"
                          v-on:close="showDatasetForm = false"
                          v-on:saved-dataset="reloadDatasets"
                          v-on:deleted-dataset="reloadDatasets" />

    <modal-dataset-import v-if="showImportForm"
                  v-bind:config="config"
                  v-bind:api="api"
                  v-on:close="showImportForm = false"
                  v-on:saved="reloadDatasets(); showImportForm = false" />

    <div v-if="!loaded && dataset === null" class="dataset-loading-indicator">
      <h2>
        <i class="fa fa-lg fa-spin fa-spinner"></i>
        Loading datasets...
      </h2>
    </div>
    <div v-else-if="dataset === null" id="dataset-manager">
      <template v-if="loaded && datasets.length === 0">
        <h2>Create a dataset...</h2>
        <p class="info-message">
          To manage institution data you must create at least one dataset. A dataset is
          a set of files that typically come from the same source and are processed in
          the same way.
        </p>
      </template>
      <template v-else>
        <h2 v-if="datasets">Datasets</h2>
        <div class="dataset-manager-list">
          <div v-for="ds in datasets" v-on:click.prevent="selectDataset(ds)" class="dataset-manager-item">
            <div class="badge badge-primary" v-bind:class="'badge-' + ds.src">
              {{stageName(ds.src)}}
              <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
            </div>
            <h3>{{ds.name}}</h3>
            <p v-if="ds.notes">{{ds.notes}}</p>
          </div>
        </div>
      </template>
      <div class="dataset-actions">
        <button v-on:click.prevent="showDatasetForm = true" class="btn btn-success">
          <i class="fa fa-plus-circle"></i>
          Create a new dataset...
        </button>
        <div class="dropdown">
          <button class="btn btn-default" v-on:click="showOptions = !showOptions">
            <i class="fa fa-fw fa-ellipsis-v"></i>
          </button>
          <div v-if="showOptions" class="dropdown-backdrop" v-on:click="showOptions = false">
          </div>
          <div v-if="showOptions" class="dropdown-menu dropdown-menu-right show">
            <button v-on:click.prevent="showImportForm = true; showOptions = false" class="btn dropdown-item">
              <i class="fa fa-file-code-o"></i>
              Import datasets from JSON
            </button>
          </div>
        </div>
      </div>
    </div>
    <template v-else>
      <ul id="stage-tabs" class="nav nav-tabs">
        <li class="nav-item">
          <a v-if="dataset.src === 'oaipmh'" href="#tab-input" class="nav-link" v-bind:class="{'active': tab === 'input'}"
             v-on:click.prevent="switchTab('input')">
            <i class="fa fw-fw fa-cloud-download"></i>
            Harvest Data
          </a>
          <a v-if="dataset.src === 'rs'" href="#tab-input" class="nav-link" v-bind:class="{'active': tab === 'input'}"
             v-on:click.prevent="switchTab('input')">
            <i class="fa fw-fw fa-clone"></i>
            Sync Data
          </a>
          <a v-if="dataset.src === 'upload'" href="#tab-input" class="nav-link" v-bind:class="{'active': tab === 'input'}"
             v-on:click.prevent="switchTab('input')">
            <i class="fa fw-fw fa-upload"></i>
            Uploads
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-convert" class="nav-link" v-bind:class="{'active': tab === 'convert'}"
             v-on:click.prevent="switchTab('convert')">
            <i class="fa fa-fw fa-file-code-o"></i>
            Transform
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-ingest" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
             v-on:click.prevent="switchTab('ingest')">
            <i class="fa fa-fw fa-database"></i>
            Ingest
          </a>
        </li>
        <li class="dataset-menu">
          <div class="dropdown">
            <button class="btn btn-info" v-on:click="showSelector = !showSelector">
              <i class="fa fa-lg fa-caret-down"></i>
              Dataset: {{dataset.name}}
            </button>
            <div v-if="showSelector" class="dropdown-backdrop" v-on:click="showSelector = false">
            </div>
            <div v-if="showSelector" class="dropdown-menu dropdown-menu-right show" id="dataset-selector">
              <a v-on:click.prevent="showSelector = false; showDatasetForm = true" class="dropdown-item" href="#">
                <i class="fa fa-edit"></i>
                Edit Dataset
              </a>
              <template v-if="datasets.length > 1">
                <div class="dropdown-divider"></div>
                <a v-for="ds in datasets" v-on:click.prevent="selectDataset(ds); showSelector = false" href="#" class="dropdown-item">
                  <i class="fa fa-fw" v-bind:class="{'fa-asterisk': ds.id===dataset.id}"></i>
                  {{ ds.name }}
                  <div class="badge badge-pill" v-bind:class="'badge-' + ds.src">
                    <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
                  </div>
                </a>
              </template>
              <div class="dropdown-divider"></div>
              <a v-on:click.prevent="closeDataset(); showSelector = false" href="#" class="dropdown-item">
                <i class="fa fa-close"></i>
                Close
              </a>
            </div>
          </div>
        </li>
      </ul>
      <div id="tab-input" class="stage-tab" v-show="tab === 'input'">
        <manager-oaipmh
            v-if="dataset.src === 'oaipmh'"
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
        <manager-rs
            v-else-if="dataset.src === 'rs'"
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
        <manager-upload
            v-else
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
      </div>
      <div id="tab-convert" class="stage-tab" v-show="tab === 'convert'">
        <manager-convert
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.output"
            v-bind:config="config"
            v-bind:active="tab === 'convert'"
            v-bind:api="api"
            v-on:error="setError" />
      </div>
      <div id="tab-ingest" class="stage-tab" v-show="tab === 'ingest'">
        <manager-ingest
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.output"
            v-bind:config="config"
            v-bind:active="tab === 'ingest'"
            v-bind:api="api"
            v-on:error="setError"  />
      </div>
    </template>
  </div>
</template>

