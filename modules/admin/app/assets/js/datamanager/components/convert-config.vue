<script>

import ModalWindow from './modal-window';
import FilePicker from './file-picker';
import DAO from '../dao';

export default {
  components: {FilePicker, ModalWindow},
  props: {
    datasetId: String,
    show: Boolean,
    api: DAO,
    config: Object,
  },
  data: function() {
    return {
      all: true,
      file: null,
      force: false,
    }
  },
  methods: {
    convert: function() {
      this.$emit("convert", this.all ? null : this.file, this.force);
      this.$emit("close");
    },
  },

}
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Transformation Configuration</template>

    <fieldset class="options-form">
      <div class="form-group form-check">
        <input class="form-check-input" id="opt-force-check" type="checkbox" v-model="force"/>
        <label class="form-check-label" for="opt-force-check">
          Rerun existing conversions
        </label>
      </div>

      <div class="form-group form-check">
        <input class="form-check-input" id="opt-all-check" type="checkbox" v-model="all"/>
        <label class="form-check-label" for="opt-all-check">
          Convert all input files
        </label>
      </div>

      <file-picker
          v-bind:disabled="all"
          v-bind:config="config"
          v-bind:api="api"
          v-bind:dataset-id="datasetId"
          v-bind:file-stage="config.input"
          v-bind:placeholder="'Select file to convert...'"
          v-model="file" />
    </fieldset>

    <template v-slot:footer>
      <button v-bind:disabled="!all && file === null" v-on:click="convert" type="button" class="btn btn-secondary">
        Run Conversion
      </button>
    </template>
  </modal-window>
</template>
