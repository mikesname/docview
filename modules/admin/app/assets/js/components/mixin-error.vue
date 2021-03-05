<script>

export default {
    data: function() {
    return {
      errored: false,
    };
  },
  methods: {
    showError: function(desc, exception) {
      this.errored = true;
      this.$emit("error", desc, exception);
    },
    clearError: function() {
      this.errored = false;
    }
  }
}
</script>
