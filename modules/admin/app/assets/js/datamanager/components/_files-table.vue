<script lang="ts">
import {prettyDate, humanFileSize} from "../common";

function emptyObj() {
  return {};
}

/**
 * Files table: this can be a fairly heavy component if there are lots of files
 * so it's rendered as a functional component with no state of its own.
 */
export default {
  props: {
    files: Array,
    fileStage: String,
    loadingMore: {
      type: Boolean,
      default: false,
    },
    loaded: {
      type: Boolean,
      default: false,
    },
    truncated: {
      type: Boolean,
      default: false
    },
    previewing: {
      type: Object,
      default: emptyObj,
    },
    validating: {
      type: Object,
      default: emptyObj,
    },
    validationResults: {
      type: Object,
      default: emptyObj,
    },
    selected: {
      type: Object,
      default: emptyObj,
    },
    deleting: {
      type: Object,
      default: emptyObj,
    },
    downloading: {
      type: Object,
      default: emptyObj,
    },
    loadingInfo: {
      type: Object,
      default: emptyObj,
    },
    filter: {
      type: String,
      default: ""
    },
  },
  filters: {
    prettyDate,
    humanFileSize,
    decodeURI,
  },
};
</script>

<template functional>
  <div v-bind:class="{'loading': !props.loaded}"
       v-on:keyup.down="listeners['select-next']()"
       v-on:keyup.up="listeners['select-prev']()"
       v-on:click.self.stop="listeners['deselect-all']()"
       class="file-list-container">
    <table class="table table-bordered table-striped table-sm" v-if="props.files.length > 0">
      <thead>
      <tr>
        <th><input type="checkbox"
                   v-bind:id="props.fileStage + '-checkall'"
                   v-bind:indeterminate.prop="Object.keys(props.selected).length > 0 && Object.keys(props.selected).length < props.files.length"
                   v-on:change="listeners['toggle-all']()" /></th>
        <th>Name</th>
        <th>Last Modified</th>
        <th>Size</th>
        <th v-bind:colspan="(
            Number(props.loadingInfo !== null) +
            Number(props.validating !== null) +
            Number(props.downloading !== null) +
            Number(props.deleting !== null))"></th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="file in props.files"
          v-bind:key="file.key"
          v-on:click.stop="listeners['show-preview'](file)"
          v-bind:class="{'active': props.previewing != null && props.previewing.key === file.key}">
        <td v-on:click.stop.prevent="listeners['toggle-file'](file)">
          <input type="checkbox"
                 v-bind:checked="Boolean(props.selected[file.key])"
                 v-on:input.stop.prevent.self="listeners['toggle-file'](file)"
                 v-on:click="$event.stopPropagation()">
        </td>
        <td>{{ file.key|decodeURI }}</td>
        <td v-bind:title="file.lastModified">{{ file.lastModified|prettyDate }}</td>
        <td>{{ file.size|humanFileSize }}</td>

        <td v-if="props.loadingInfo !== null">
          <a href="#" v-on:click.prevent.stop="listeners['info'](file.key)">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': props.loadingInfo[file.key],
                'fa-info-circle': !props.loadingInfo[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="props.validating !== null">
            <a href="#" v-on:click.prevent.stop="listeners['validate-files']({[file.eTag]: file.key})">
            <i v-if="props.validating[file.eTag]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            <i v-else-if="props.validationResults && props.validationResults[file.eTag]" class="fa fa-fw" v-bind:class="{
                    'fa-check text-success': props.validationResults[file.eTag].length === 0,
                    'fa-exclamation-circle text-danger': props.validationResults[file.eTag].length > 0
                    }">
            </i>
            <i v-else class="fa fa-fw fa-flag-o"></i>
          </a>
        </td>
        <td v-if="props.downloading !== null">
          <a href="#" title="" v-on:click.prevent.stop="listeners['download-files']([file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': props.downloading[file.key],
                'fa-download': !props.downloading[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="props.deleting !== null">
          <a href="#" v-on:click.prevent.stop="listeners['delete-files']([file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': props.deleting[file.key],
                'fa-trash-o': !props.deleting[file.key]
              }"></i>
          </a>
        </td>
      </tr>
      </tbody>
    </table>
    <button class="btn btn-sm btn-default" v-if="props.truncated" v-on:click.prevent.stop="listeners['load-more']()">
      Load more
      <i v-if="props.loadingMore" class="fa fa-fw fa-cog fa-spin"/>
      <i v-else class="fa fa-fw fa-caret-down"/>
    </button>
    <div class="panel-placeholder" v-else-if="props.loaded && props.filter && props.files.length === 0">
      No files found starting with &quot;<code>{{ props.filter }}</code>&quot;...
    </div>
    <div class="panel-placeholder" v-else-if="props.loaded && props.files.length === 0">
      There are no files here yet.
    </div>
    <div class="file-list-loading-indicator" v-show="!props.loaded">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>

