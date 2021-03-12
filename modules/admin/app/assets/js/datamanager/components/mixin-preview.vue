<script>
export default {
  data: function() {
    return {
      tab: 'preview'
    }
  },
  methods: {
    showPreview: function (file) {
      this.previewing = file;
      this.tab = 'preview';
    },
  }
}
</script>
