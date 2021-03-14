<script lang="ts">


import _debounce from 'lodash/debounce';
import _forIn from 'lodash/forIn';
import _fromPairs from 'lodash/fromPairs';
import _isEmpty from 'lodash/isEmpty';

import {DAO, FileMeta} from '../dao';


let initialStageState = function(): object {
  return {
    loaded: false,
    loadingMore: false,
    truncated: false,
    tab: 'preview',
    previewing: null,
    deleting: {},
    downloading: {},
    loadingInfo: {},
    selected: {},
    filter: {
      value: "",
      active: false
    },
    files: [],
    log: [],
    fileInfo: null,
  };
};

export default {
  props: {
    datasetId: String,
    active: Boolean,
    api: DAO,
  },
  data: function(): object {
    return initialStageState();
  },
  computed: {
    selectedKeys: function (): string[] {
      return Object.keys(this.selected);
    },
    selectedTags: function(): object {
      return _fromPairs(Object.values(this.selected).map((f: FileMeta) => [f.eTag, f.key]));
    }
  },
  methods: {
    reset: function() {
      Object.assign(this.$data, initialStageState());
    },
    clearFilter: function (): Promise<void> {
      this.filter.value = "";
      return this.refresh();
    },
    filterFiles: function (): Promise<void> {
      let func = () => {
        this.filter.active = true;
        return this.load().then(r => {
          this.filter.active = false;
          return r;
        });
      };
      return _debounce(func, 300)();
    },
    refresh: _debounce(function() {
      return this.load();
    }, 500),
    load: function (): Promise<void> {
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value)
        .then(data => {
          this.files = data.files;
          this.truncated = data.truncated;
        })
        .catch(error => this.showError(`Error retrieving file list for stage ${this.fileStage}: `, error))
        .finally(() => this.loaded = true);
    },
    loadMore: function(): Promise<void> {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key
        : null;
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value, from)
        .then(data => {
          this.files.push.apply(this.files, data.files);
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loadingMore = false);
    },
    downloadFiles: function(keys) {
      keys.forEach(key => this.$set(this.downloading, key, true));
      this.api.fileUrls(this.datasetId, this.fileStage, keys)
        .then(urls => {
          _forIn(urls, (url, fileName) => {
            window.open(url, '_blank');
            this.$delete(this.downloading, fileName);
          });
        })
        .catch(error => this.showError("Error fetching download URLs", error))
        .finally(() => this.downloading = {});
    },
    deleteFiles: function (keys) {
      if (_isEmpty(keys) || keys.includes(this.previewing)) {
        this.previewing = null;
      }
      let dkeys = _isEmpty(keys) ? this.files.map(f => f.key) : keys;
      dkeys.forEach(key => this.$set(this.deleting, key, true));
      this.api.deleteFiles(this.datasetId, this.fileStage, keys)
        .then(() => {
          dkeys.forEach(key => {
            this.$delete(this.deleting, key);
            this.$delete(this.selected, key);
          });
          this.refresh();
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    info: function(key) {
      this.$set(this.loadingInfo, key, true);
      return this.api.info(this.datasetId, this.fileStage, key)
        .then(r => this.fileInfo = r)
        .catch(error => this.showError("Error fetching file info", error))
        .finally(() => this.loadingInfo = {});
    },
    selectItem: function(file) {
      this.$set(this.selected, file.key, file);
    },
    deselectItem: function(file) {
      this.$delete(this.selected, file.key);
    },
    showError: function(msg: string, exp?: object) {}, // Overridden by inheritors
    deselect: function() {
      this.previewing = null;
    }
  },
  watch: {
    active: function(newValue) {
      if (newValue) {
        this.load();
      }
    },
    datasetId: function() {
      this.reset();
      this.load();
    }
  },
  created: function() {
    this.load();
  }
}
</script>
