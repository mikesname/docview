<script lang="ts">

import DatasetManager from './components/_dataset-manager';
import MixinUtil from './components/_mixin-util';
import {DAO} from "./dao";


if (typeof window !== 'undefined') {
  // Prevent default drag/drop action...
  window.addEventListener("dragover", e => e.preventDefault(), false);
  window.addEventListener("drop", e => e.preventDefault(), false);
}

export default {
  components: {DatasetManager},
  mixins: [MixinUtil],
  data: function() {
    return {
      config: CONFIG,
      api: new DAO(SERVICE, CONFIG.repoId),
      tab: 'input',
    }
  },
}
</script>

<template>
  <div id="app-container">
    <dataset-manager
        v-bind:config="config"
        v-bind:init-tab="tab"
        v-bind:api="api" />
  </div>
</template>
