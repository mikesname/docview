<script lang="ts">

import FilePickerSuggestion from './_file-picker-suggestion';

import _debounce from 'lodash/debounce';
import {DAO} from '../dao';

export default {
  components: {FilePickerSuggestion},
  props: {
    datasetId: String,
    fileStage: String,
    value: Object,
    disabled: Boolean,
    api: DAO,
    config: Object,
    placeholder: {
      type: String,
      default: "Select file..."
    }
  },
  data: function (): object {
    return {
      text: null,
      selectedIdx: -1,
      suggestions: [],
      loading: false,
      showSuggestions: false,
    }
  },
  methods: {
    search: function () {
      this.loading = true;
      let list = () => {
        this.api.listFiles(this.datasetId, this.fileStage, this.text ? this.text : "")
            .then(data => {
              this.suggestions = data.files;
              this.showSuggestions = true;
            })
            .finally(() => this.loading = false);
      }
      _debounce(list, 300)();
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
    },
    setAndChooseItem: function (item) {
      this.$emit("input", item);
      this.cancelComplete();
      this.text = null;
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
          len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setAndChooseItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.$emit('input', null);
      }
    },
    cancelComplete: function () {
      this.$nextTick(() => {
        this.suggestions = [];
        this.selectedIdx = -1;
        this.showSuggestions = false;
      })
    }
  },
};
</script>

<template>
  <div class="file-picker">
    <div class="file-picker-input-container">
      <div v-show="showSuggestions" class="dropdown-backdrop" v-on:click="cancelComplete"></div>
      <label class="control-label sr-only">File:</label>
      <input class="file-picker-input form-control form-control-sm" type="text" v-bind:placeholder="placeholder"
             v-bind:disabled="disabled"
             v-bind:value="text !== null ? text : (value ? value.key : '')"
             v-on:focus="search"
             v-on:input="text = $event.target.value; search()"
             v-on:keydown.up="selectPrev"
             v-on:keydown.down="selectNext"
             v-on:keydown.enter="setItemFromSelection"
             v-on:keydown.esc="cancelComplete"/>
      <i v-if="loading" class="loading-indicator fa fa-circle-o-notch fa-fw fa-spin"></i>
      <div v-if="showSuggestions" class="file-picker-suggestions dropdown-list">
        <file-picker-suggestion
            v-for="(suggestion, i) in suggestions"
            v-bind:class="{selected: i === selectedIdx}"
            v-bind:key="suggestion.key"
            v-bind:item="suggestion"
            v-bind:selected="i === selectedIdx"
            v-on:selected="setAndChooseItem"/>
        <div v-if="!loading && suggestions.length === 0" class="file-picker-suggestions-empty">
          No files found...
        </div>
      </div>
    </div>
  </div>
</template>

