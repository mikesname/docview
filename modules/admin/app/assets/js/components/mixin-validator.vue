<script>
  export default {
  props: {
    datasetId: String,
    fileStage: String,
    api: Object,
  },
  data: function () {
    return {
      validating: {},
      validationRunning: false,
      validationResults: {},
      validationLog: [],
    }
  },
  methods: {
    handleValidationResults: function (errs) {
      _.forEach(errs, item => {
        this.$set(this.validationResults, item.eTag, item.errors)
        this.$delete(this.validating, item.eTag);
      });
      if (_.isUndefined(_.find(errs, (err) => err.errors.length > 0))) {
        this.validationLog.push('<span class="text-success">No errors found ✓</span>');
      } else {
        errs.forEach(item => {
          if (item.errors.length > 0) {
            this.validationLog.push('<span class="text-danger">' + decodeURI(item.key) + ':</span>')
            item.errors.forEach(err => {
              this.validationLog.push("    " + err.line + "/" + err.pos + " - " + err.error);
            })
          }
        });
      }
    },
    validateFiles: function (tagToKey) {
      this.tab = 'validation';
      this.validationRunning = true;
      this.validationLog = [];
      let allTags = _.isEmpty(tagToKey) ? this.files.map(f => f.eTag) : _.keys(tagToKey);
      _.forEach(allTags, tag => this.$set(this.validating, tag, true));

      this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
        .then(errs => this.handleValidationResults(errs))
        .catch(error => this.showError("Error attempting validation", error))
        .finally(() => {
          this.validating = {};
          this.validationRunning = false;
        });
    },
  }
}
</script>
