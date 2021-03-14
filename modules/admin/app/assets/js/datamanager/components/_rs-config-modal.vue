<script>

import ModalWindow from './_modal-window';
import {DAO} from '../dao';

export default {
  components: {ModalWindow},
  props: {
    waiting: Boolean,
    datasetId: String,
    config: Object,
    api: DAO,
  },
  data: function() {
    return {
      url: this.config ? this.config.url : null,
      filter: this.config ? this.config.filter : null,
      tested: null,
      testing: false,
      error: null,
    }
  },
  computed: {
    isValidConfig: function() {
      return this.url && this.url.trim() !== "";
    },
  },
  methods: {
    save: function() {
      this.$emit("saving");
      this.api.saveSyncConfig(this.datasetId, {url: this.url, filter: this.filter})
          .then(data => this.$emit("saved-config", data))
          .catch(error => this.$emit("error", "Error saving RS config", error));
    },
    testEndpoint: function() {
      this.testing = true;
      this.api.testSyncConfig(this.datasetId, {url: this.url, filter: this.filter})
          .then(() => {
            this.tested = true;
            this.error = null;
          })
          .catch(e => {
            this.tested = false;
            let err = e.response.data;
            if (err.error) {
              this.error = err.error;
            }
          })
          .finally(() => this.testing = false);
    }
  },
  watch: {
    config: function(newValue) {
      this.url = newValue ? newValue.url : null;
      this.filter = newValue ? newValue.filter : null;
    }
  },
}
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>ResourceSync Endpoint Configuration</template>

    <div class="options-form">
      <div class="form-group">
        <label class="form-label" for="opt-endpoint-url">
          ResourceSync capability list endpoint URL
        </label>
        <input class="form-control" id="opt-endpoint-url" type="url" v-model.trim="url" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-filter">
          ResourceSync path filter RegEx
        </label>
        <input class="form-control" id="opt-filter" type="text" v-model.trim="filter" placeholder="(optional)"/>
      </div>
      <div id="endpoint-errors">
        <span v-if="tested === null">&nbsp;</span>
        <span v-else-if="tested" class="text-success">No errors detected</span>
        <span v-else-if="error" class="text-danger">{{error}}</span>
        <span v-else class="text-danger">Test unsuccessful</span>
      </div>
    </div>

    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Cancel
      </button>
      <button v-bind:disabled="!isValidConfig"
              v-on:click="testEndpoint" type="button" class="btn btn-default">
        <i v-if="testing" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        <i v-else-if="tested === null" class="fa fa-fw fa-question"/>
        <i v-else-if="tested" class="fa fa-fw fa-check text-success"/>
        <i v-else class="fa fa-fw fa-close text-danger"/>
        Test Endpoint
      </button>
      <button v-bind:disabled="!isValidConfig"
              v-on:click="save" type="button" class="btn btn-secondary">
        <i v-bind:class="{'fa-clone': !waiting, 'fa-circle-o-notch fa-spin': waiting}" class="fa fa-fw"></i>
        Sync Endpoint
      </button>
    </template>
  </modal-window>
</template>

