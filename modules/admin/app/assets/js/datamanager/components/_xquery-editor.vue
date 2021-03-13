<script lang="ts">

import Vue from 'vue';

import _padStart from 'lodash/padStart';
import _clone from 'lodash/clone';
import _concat from 'lodash/concat';

export default {
  props: {
    value: String,
  },
  data: function(): Object {
    return {
      mappings: this.deserialize(this.value),
      selected: -1,
    }
  },
  methods: {
    _padStart,

    update: function(): Promise<void> {
      this.$emit('input', this.serialize(this.mappings));
      // Return a promise when the DOM is ready...
      return Vue.nextTick();
    },
    focus: function(row, col): void {
      let elem = this.$refs[_padStart(row, 4, 0) + '-' + col];
      if (elem && elem[0]) {
        elem[0].focus();
      }
    },
    add: function(): void {
      // Insert a new item below the current selection, or
      // at the end if nothing is selected.
      let point = this.selected === -1
          ? this.mappings.length
          : this.selected + 1;
      this.mappings.splice(point, 0, ["", "", "", ""])
      this.selected = point;
      this.update()
          .then(() => this.focus(this.selected, 0));
    },
    duplicate: function(i): void {
      let m = _clone(this.mappings[i]);
      this.selected = i + 1;
      this.mappings.splice(this.selected, 0, m);
      this.update();
    },
    remove: function(i): void {
      this.mappings.splice(i, 1);
      this.selected = Math.min(i, this.mappings.length - 1);
      this.update();
    },
    moveUp: function(i): void {
      if (i > 0) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i - 1, 0, m);
        this.selected = i - 1;
        this.update();
      }
    },
    moveDown: function(i): void {
      if (i < this.mappings.length - 1) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i + 1, 0, m);
        this.selected = i + 1;
        this.update();
      }
    },
    deserialize: function(str): string[] {
      if (str !== "") {
        // Ignore the header row here...
        return str
            .split("\n")
            .slice(1)
            .map (m => {
              let parts = m.split("\t");
              return [
                parts[0] ? parts[0] : "",
                parts[1] ? parts[1] : "",
                parts[2] ? parts[2] : "",
                parts[3] ? parts[3] : "",
              ];
            });
      } else {
        return [];
      }
    },
    serialize: function(mappings): string {
      let header = ["target-path\ttarget-node\tsource-node\tvalue"]
      let rows = mappings.map(m => m.join("\t"))
      let all = _concat(header, rows)
      return all.join("\n");
    },
  },
};
</script>

<template>
  <div class="xquery-editor">
    <div class="xquery-editor-data" v-on:keyup.esc="selected = -1">
      <div class="xquery-editor-header">
        <input readonly disabled type="text" value="target-path" @click="selected = -1"/>
        <input readonly disabled type="text" value="target-node" @click="selected = -1"/>
        <input readonly disabled type="text" value="source-node" @click="selected = -1"/>
        <input readonly disabled type="text" value="value" @click="selected = -1"/>
      </div>
      <div class="xquery-editor-mappings">
        <template v-for="(mapping, row) in mappings">
          <input
              v-for="col in [0, 1, 2, 3]"
              type="text"
              v-bind:ref="_padStart(row, 4, 0) + '-' + col"
              v-bind:key="_padStart(row, 4, 0) + '-' + col"
              v-bind:class="{'selected': selected === row}"
              v-model="mappings[row][col]"
              @change="update"
              @focusin="selected = row" />
        </template>
      </div>
    </div>
    <div class="xquery-editor-toolbar">
      <button class="btn btn-default btn-sm" v-on:click="add">
        <i class="fa fa-plus"></i>
        Add Mapping
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="duplicate(selected)">
        <i class="fa fa-copy"></i>
        Duplicate Mapping
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="remove(selected)">
        <i class="fa fa-trash-o"></i>
        Delete Mapping
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === 0" v-on:click="moveUp(selected)">
        <i class="fa fa-caret-up"></i>
        Move Up
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === mappings.length - 1" v-on:click="moveDown(selected)">
        <i class="fa fa-caret-down"></i>
        Move Down
      </button>
      <div class="xquery-editor-toolbar-info">
        Data mappings: {{mappings.length}}
      </div>
    </div>
  </div>
</template>

