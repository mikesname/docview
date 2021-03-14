<script>

import {prettyDate, humanFileSize} from "../common";
import ModalWindow from './_modal-window';

export default {
  components: {ModalWindow},
  props: {
    fileInfo: Object
  },
  methods: { prettyDate, humanFileSize, decodeURI }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>{{ decodeURI(fileInfo.meta.key) }}</template>
    <dl>
      <dt>File size:</dt>
      <dd>{{ humanFileSize(fileInfo.meta.size) }}</dd>
      <dt>Last Modified:</dt>
      <dd v-bind:title="fileInfo.meta.lastModified">{{ prettyDate(fileInfo.meta.lastModified) }}</dd>
      <dt>E-Tag:</dt>
      <dd v-bind:title="fileInfo.meta.eTag">{{fileInfo.meta.eTag}}</dd>
    </dl>
    <template v-if="fileInfo.versions && fileInfo.versions.length > 1">
      <h4>Version History</h4>
      <table class="version-history-list table table-striped table-sm table-bordered">
        <tr>
          <th>ID</th>
          <th>Created</th>
          <th>Size</th>
        </tr>
        <tr v-for="version in fileInfo.versions">
          <td>{{version.versionId}}</td>
          <td v-bind:title="version.lastModified">{{ prettyDate(version.lastModified) }}</td>
          <td>{{ humanFileSize(version.size) }}</td>
        </tr>
      </table>
    </template>
    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Close
      </button>
    </template>
  </modal-window>
</template>

