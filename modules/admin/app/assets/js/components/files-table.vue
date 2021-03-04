<script>
export default {
  props: {
    api: DAO,
    fileStage: String,
    loadingMore: Boolean,
    dropping: Boolean,
    loaded: Boolean,
    previewing: Object,
    validating: Object,
    validationResults: Object,
    files: Array,
    selected: Object,
    truncated: Boolean,
    deleting: Object,
    downloading: Object,
    loadingInfo: Object,
    filter: String,
    info: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    allChecked: function () {
      return Object.keys(this.selected).length === this.files.length;
    },
    utilRows: function() {
      return Number(this.deleted !== null) +
          Number(this.validating !== null) +
          Number(this.deleting !== null) +
          Number(this.downloading !== null) +
          Number(this.loadingInfo !== null);
    }
  },
  methods: {
    toggleAll: function (checked) {
      this.files.forEach(f => this.toggleItem(f, checked));
    },
    toggleItem: function (file, checked) {
      if (checked) {
        this.$emit('item-selected', file);
      } else {
        this.$emit('item-deselected', file);
      }
    },
    isPreviewing: function(file) {
      return this.previewing !== null && this.previewing.key === file.key;
    }
  },
  watch: {
    selected: function (newValue) {
      let selected = Object.keys(newValue).length;
      let checkAll = this.$el.querySelector("#" + this.fileStage + "-checkall");
      if (checkAll) {
        checkAll.indeterminate = selected > 0 && selected !== this.files.length;
      }
    },
  },

};
</script>

<template>
  <div v-bind:class="{'loading': !loaded, 'dropping': dropping}"
       v-on:keyup.down="$emit('select-next')"
       v-on:keyup.up="$emit('select-prev')"
       v-on:click.self.stop="$emit('deselect-all')"
       class="file-list-container">
    <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
      <thead>
      <tr>
        <th><input type="checkbox" v-bind:id="fileStage + '-checkall'" v-on:input="toggleAll($event.target.checked)"/></th>
        <th>Name</th>
        <th>Last Modified</th>
        <th>Size</th>
        <th v-bind:colspan="utilRows"></th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="file in files"
          v-bind:key="file.key"
          v-on:click.stop="$emit('show-preview', file)"
          v-bind:class="{'active': isPreviewing(file)}">
        <td v-on:click.stop.prevent.self="toggleItem(file, !selected[file.key])">
          <input type="checkbox" v-bind:checked="Boolean(selected[file.key])"
                 v-on:input.stop.prevent.self="toggleItem(file, !selected[file.key])"
                 v-on:click="$event.stopPropagation()">
        </td>
        <td>{{file.key|decodeUri}}</td>
        <td v-bind:title="file.lastModified">{{file.lastModified | prettyDate}}</td>
        <td>{{file.size | humanFileSize(true)}}</td>

        <td v-if="loadingInfo !== null">
          <a href="#" v-on:click.prevent.stop="$emit('info', file.key)">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': loadingInfo[file.key],
                'fa-info-circle': !loadingInfo[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="validating !== null">
          <a href="#" v-on:click.prevent.stop="$emit('validate-files', _.fromPairs([[file.eTag, file.key]]))">
            <i v-if="validating[file.eTag]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            <i v-else-if="validationResults && validationResults[file.eTag]" class="fa fa-fw" v-bind:class="{
                    'fa-check text-success': validationResults[file.eTag].length === 0,
                    'fa-exclamation-circle text-danger': validationResults[file.eTag].length > 0
                    }">
            </i>
            <i v-else class="fa fa-fw fa-flag-o"></i>
          </a>
        </td>
        <td v-if="downloading !== null">
          <a href="#" title="" v-on:click.prevent.stop="$emit('download-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': downloading[file.key],
                'fa-download': !downloading[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="deleting !== null">
          <a href="#" v-on:click.prevent.stop="$emit('delete-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': deleting[file.key],
                'fa-trash-o': !deleting[file.key]
              }"></i>
          </a>
        </td>
      </tr>
      </tbody>
    </table>
    <button class="btn btn-sm btn-default" v-if="truncated" v-on:click.prevent.stop="$emit('load-more')">
      Load more
      <i v-if="loadingMore" class="fa fa-fw fa-cog fa-spin"/>
      <i v-else class="fa fa-fw fa-caret-down"/>
    </button>
    <div class="panel-placeholder" v-else-if="loaded && filter && files.length === 0">
      No files found starting with &quot;<code>{{filter}}</code>&quot;...
    </div>
    <div class="panel-placeholder" v-else-if="loaded && files.length === 0">
      There are no files here yet.
    </div>
    <div class="file-list-loading-indicator" v-show="!loaded">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>

