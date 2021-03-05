<script>

import MixinPreviewPanel from './mixin-preview-panel';

export default {
  mixins: [MixinPreviewPanel],
  props: {
    mappings: Array,
    trigger: String,
    api: Object,
  },
  methods: {
    validate: function() {
      // FIXME: not yet supported
    },
    load: function() {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.worker.postMessage({
        type: 'convert-preview',
        url: this.api.convertFileUrl(this.datasetId, this.fileStage, this.previewing.key),
        mappings: this.mappings,
        max: this.config.maxPreviewSize,
      });
    }
  },
  watch: {
    trigger: function() {
      this.load();
    },
    config: function(newConfig, oldConfig) {
      if (newConfig !== oldConfig && newConfig !== null) {
        console.log("Refresh convert preview...");
        this.load();
      }
    }
  }
}
</script>
