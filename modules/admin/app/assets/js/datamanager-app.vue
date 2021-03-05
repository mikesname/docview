
<script>
if (typeof window !== 'undefined') {
  // Prevent default drag/drop action...
  window.addEventListener("dragover", e => e.preventDefault(), false);
  window.addEventListener("drop", e => e.preventDefault(), false);
}

import DatasetManager from './components/dataset-manager';
import MixinUtil from './components/mixin-util';

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
