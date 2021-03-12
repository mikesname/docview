<script>

import {prettyDate, humanFileSize} from "../common";
import ModalWindow from './modal-window';

export default {
  components: {ModalWindow},
  props: {
    fileInfo: Object
  },
  filters: { prettyDate, humanFileSize, decodeURI }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>{{fileInfo.meta.key|decodeURI}}</template>
    <dl>
      <dt>File size:</dt>
      <dd>{{fileInfo.meta.size|humanFileSize}}</dd>
      <dt>Last Modified:</dt>
      <dd v-bind:title="fileInfo.meta.lastModified">{{fileInfo.meta.lastModified|prettyDate}}</dd>
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
          <td v-bind:title="version.lastModified">{{version.lastModified|prettyDate}}</td>
          <td>{{version.size|humanFileSize}}</td>
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

