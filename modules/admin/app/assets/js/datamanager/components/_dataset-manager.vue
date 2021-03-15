<script lang="ts">

import DatasetForm from './_dataset-form';
import OaipmhManager from './_oaipmh-manager';
import UploadManager from './_upload-manager';
import IngestManager from './_ingest-manager';
import RsManager from './_rs-manager';
import ConvertManager from './_convert-manager';

import MixinUtil from './_mixin-util';
import {DAO, ImportDataset, ImportDatasetSrc} from '../dao';

import _find from 'lodash/find';
import _merge from 'lodash/merge';
import _omit from 'lodash/omit';

export default {
  components: {DatasetForm, OaipmhManager, UploadManager, IngestManager, RsManager, ConvertManager},
  mixins: [MixinUtil],
  props: {
    config: Object,
    api: DAO,
    initTab: String,
  },
  data: function() {
    return {
      loaded: false,
      datasets: [],
      dataset: null,
      tab: this.initTab,
      error: null,
      showForm: false,
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
    showNewDatasetForm: function () {
      this.showForm = true;
    },
    loadDatasets: function() {
      this.api.datasetStats().then(stats => this.stats = stats);
      return this.api.listDatasets()
          .then(dsl => this.datasets = dsl)
          .catch(e => this.showError("Error loading datasets", e))
          .finally(() => this.loaded = true);
    },
    reloadDatasets: function(ds?: ImportDataset) {
      this.loadDatasets().then(() => this.selectDataset(ds));
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
  <div id="data-manager-container" class="container">
    <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
      <span class="close" v-on:click="error = null">&times;</span>
      {{error}}
    </div>
    <dataset-form v-if="showForm"
                  v-bind:info="dataset"
                  v-bind:config="config"
                  v-bind:api="api"
                  v-on:close="showForm = false"
                  v-on:saved-dataset="reloadDatasets"
                  v-on:deleted-dataset="reloadDatasets" />

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
        <h2 v-if="datasets">Select dataset:</h2>
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
      <button v-on:click.prevent="showNewDatasetForm" class="btn btn-success">
        <i class="fa fa-plus-circle"></i>
        Create a new dataset...
      </button>
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
            <div v-if="showSelector" class="dropdown-menu dropdown-menu-right show">
              <a v-on:click.prevent="showSelector = false; showForm = true" class="dropdown-item" href="#">
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
        <oaipmh-manager
            v-if="dataset.src === 'oaipmh'"
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
        <rs-manager
            v-else-if="dataset.src === 'rs'"
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
        <upload-manager
            v-else
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
      </div>
      <div id="tab-convert" class="stage-tab" v-show="tab === 'convert'">
        <convert-manager
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.output"
            v-bind:config="config"
            v-bind:active="tab === 'convert'"
            v-bind:api="api"
            v-on:error="setError" />
      </div>
      <div id="tab-ingest" class="stage-tab" v-show="tab === 'ingest'">
        <ingest-manager
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

