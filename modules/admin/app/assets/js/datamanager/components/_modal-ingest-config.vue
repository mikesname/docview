<script lang="ts">

import ModalWindow from './_modal-window';
import {FileMeta} from '../types';
import {DatasetManagerApi} from "../api";

import _pick from 'lodash/pick';
import _size from 'lodash/size';


export default {
  components: {ModalWindow},
  props: {
    datasetId: String,
    api: DatasetManagerApi,
    config: Object,
    opts: Object,
    waiting: Boolean,
    props: Array,
  },
  data: function(): object {
    return {
      allowUpdates: this.opts ? this.opts.allowUpdates : false,
      useSourceId: this.opts ? this.opts.useSourceId : false,
      tolerant: this.opts ? this.opts.tolerant : false,
      defaultLang: this.opts ? this.opts.defaultLang : null,
      properties: this.opts ? this.opts.properties : null,
      logMessage: this.opts ? this.opts.logMessage : "",
      loading: false,
      commit: false,
      error: null,
    }
  },
  methods: {
    submit: function() {
      this.$emit("saving");
      this.api.saveImportConfig(
          this.datasetId, {
            allowUpdates: this.allowUpdates,
            useSourceId: this.useSourceId,
            tolerant: this.tolerant,
            defaultLang: this.defaultLang,
            properties: this.properties,
            logMessage: this.logMessage,
          })
          .then(data => this.$emit("saved-config", data, this.commit))
          .catch(error => this.$emit("error", "Error saving import config", error));
    },
    uploadProperties: function(event: Event | DragEvent) {
      this.loading = true;
      let fileList = event.dataTransfer
          ? event.dataTransfer.files
          : event.target.files;

      if (fileList.length > 0) {
        let file = fileList[0];

        // NB: the fileStage arg here is 'config', since we are uploading a config file, rather then
        // the stage of the ingest manager ('output').
        this.api.uploadHandle(this.datasetId, this.config.config, _pick(file, ['name', 'type', 'size']))
            .then(data => this.api
                .uploadFile(data.presignedUrl, file, () => true)
                .then(() => {
                  this.$emit("update");
                  this.properties = file.name;
                  if (event.target.files) {
                    event.target.files = null;
                  }
                })
            )
            .catch(e => this.error = "Error uploading properties: " + e)
            .finally(() => this.loading = false);
      }
    },
    deleteProperties: function(file: FileMeta) {
      this.loading = true;
      if (file.key === this.properties) {
        this.properties = null;
      }
      this.api.deleteFiles(this.datasetId, this.config.config, [file.key])
          .then(() => this.$emit("update"))
          .finally(() => this.loading = false);
    },
    selectPropFile: function(file: FileMeta) {
      this.properties = this.properties === file.key ? null : file.key;
    }
  },
  computed: {
    isValidConfig: function() {
      return this.logMessage && this.logMessage.trim() !== "";
    },
    hasProps: function() {
      return _size(this.props) > 0;
    }
  },
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Ingest Settings</template>

    <fieldset v-bind:disabled="loading" class="options-form">
      <div class="form-group form-check">
        <input v-model="allowUpdates" class="form-check-input" id="opt-allowUpdates-check" type="checkbox"/>
        <label class="form-check-label" for="opt-allowUpdates-check">
          Allow updates: check this if it is expected that existing items will be modified
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="tolerant" class="form-check-input" id="opt-tolerant-check" type="checkbox"/>
        <label class="form-check-label" for="opt-tolerant-check">
          Tolerant mode: do not abort on individual item validation errors
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="useSourceId" class="form-check-input" id="opt-useSourceId-check" type="checkbox"/>
        <label class="form-check-label" for="opt-useSourceId-check">
          Use source file ID as well as language code to identify descriptions (this will allow adding multiple
          descriptions in the same language if they come from different EAD files)
        </label>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-new-props">
          Properties File
          <span class="text-success" title="Upload Properties File" id="opt-new-props">
              <i class="fa fa-plus-circle"></i>
              <label class="sr-only" for="opt-new-props-input">Upload Properties File...</label>
              <input v-on:change.prevent="uploadProperties" id="opt-new-props-input"
                     type="file" pattern=".*.properties$"/>
            </span>
        </label>
        <div class="ingest-options-properties-container">
          <table v-if="hasProps" class="ingest-options-properties table table-bordered table-sm table-striped">
            <tr v-for="f in props" v-on:click="selectPropFile(f)" v-bind:class="{'active': f.key===properties}">
              <td><i v-bind:class="{
                  'fa-check': f.key===properties,
                  'text-success': f.key===properties,
                  'fa-minus': f.key!==properties,
                  'text-muted': f.key!==properties,
                }" class="fa fa-fw"></i></td>
              <td>{{f.key}}</td>
              <td v-on:click.stop.prevent="deleteProperties(f)"><i class="fa fa-trash-o"></i></td>
            </tr>
          </table>
          <div v-else-if="loading" class="panel-placeholder">
            Loading properties...
          </div>
          <div v-else class="panel-placeholder">
            No custom properties...
            <input class="opt-new-props-input"
                   type="file" pattern=".*.properties$" v-on:change.prevent="uploadProperties"/>
          </div>
        </div>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-log-message">Log Message</label>
        <input v-model="logMessage" class="form-control form-control-sm" id="opt-log-message" placeholder="(required)"/>
      </div>
      <div class="form-group form-check">
        <input tabindex="-1" v-model="commit" class="form-check-input" id="opt-commit-check" type="checkbox"/>
        <label class="form-check-label" for="opt-commit-check">
          Commit ingest: make changes to database
        </label>
      </div>
      <div v-if="error" class="alert alert-danger">
        {{error}}
      </div>
    </fieldset>

    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Cancel
      </button>
      <button v-bind:disabled="!isValidConfig" v-on:click="submit"
              v-bind:class="{'btn-danger': commit, 'btn-secondary': !commit}"
              type="button" class="btn">
        <i v-if="!waiting" class="fa fa-fw fa-database"></i>
        <i v-else class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <template v-if="commit">Run Ingest</template>
        <template v-else>Start Dry Run</template>
      </button>
    </template>
  </modal-window>
</template>
