<script>

import _extend from 'lodash/extend';
import _isEmpty from 'lodash/isEmpty';
import _has from 'lodash/has';
import _fromPairs from 'lodash/fromPairs';
import _toPairs from 'lodash/toPairs';
import _omit from 'lodash/omit';
import _set from 'lodash/set';

export default {
  methods: {
    removeQueryParam: function(qs, names) {
      let qp = this.queryParams(qs);
      return this.queryString(_omit(qp, names));
    },
    setQueryParam: function(qs, name, value) {
      let qp = this.queryParams(qs);
      return this.queryString(_set(qp, name, value));
    },
    getQueryParam: function(qs, name) {
      let qp = this.queryParams(qs);
      return _has(qp, name) ? qp[name] : null;
    },
    queryParams: function(qs) {
      let qsp = (qs && qs[0] === '?') ? qs.slice(1): qs;
      return (qsp && qsp.trim() !== "")
        ? _fromPairs(qsp.split("&").map(p => p.split("=")))
        : {};
    },
    queryString: function(qp) {
      return !_isEmpty(qp)
        ? ("?" + _toPairs(qp).map(p => p.join("=")).join("&"))
        : "";
    },
    removeUrlState: function(key) {
      history.replaceState(
        _omit(this.queryParams(window.location.search), key),
        document.title,
        this.removeQueryParam(window.location.search, key)
      );
    },
    replaceUrlState: function(key, value) {
      history.replaceState(
        _extend(this.queryParams(window.location.search), {key: value}),
        document.title,
        this.setQueryParam(window.location.search, key, value)
      );
    }
  },

  filters: { decodeURI }
}
</script>
