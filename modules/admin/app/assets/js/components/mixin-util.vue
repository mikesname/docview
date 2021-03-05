<script>

export default {
  methods: {
    removeQueryParam: function(qs, names) {
      let qp = this.queryParams(qs);
      return this.queryString(_.omit(qp, names));
    },
    setQueryParam: function(qs, name, value) {
      let qp = this.queryParams(qs);
      return this.queryString(_.set(qp, name, value));
    },
    getQueryParam: function(qs, name) {
      let qp = this.queryParams(qs);
      return _.has(qp, name) ? qp[name] : null;
    },
    queryParams: function(qs) {
      let qsp = (qs && qs[0] === '?') ? qs.slice(1): qs;
      return (qsp && qsp.trim() !== "")
        ? _.fromPairs(qsp.split("&").map(p => p.split("=")))
        : {};
    },
    queryString: function(qp) {
      return !_.isEmpty(qp)
        ? ("?" + _.toPairs(qp).map(p => p.join("=")).join("&"))
        : "";
    },
    removeUrlState: function(key) {
      history.replaceState(
        _.omit(this.queryParams(window.location.search), key),
        document.title,
        this.removeQueryParam(window.location.search, key)
      );
    },
    replaceUrlState: function(key, value) {
      history.replaceState(
        _.extend(this.queryParams(window.location.search), {key: value}),
        document.title,
        this.setQueryParam(window.location.search, key, value)
      );
    }
  }
}
</script>
