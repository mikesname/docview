<script>
export default {
  props: {
    item: Object,
    enabled: Boolean,
  },
}
</script>

<template>
  <div v-bind:class="{'enabled': enabled}"
       v-on:dblclick="$emit('edit')"
       class="list-group-item transformation-item list-group-item-action">
    <h4 class="transformation-item-name">
      {{item.name}}
      <span class="transformation-item-comments" v-bind:title="item.comments">{{item.comments}}</span>
    </h4>
    <button class="transformation-item-edit btn btn-sm btn-default" v-on:click="$emit('edit')">
      <i class="fa fa-edit"></i>
    </button>
    <span class="transformation-item-meta">
        <span class="badge badge-pill" v-bind:class="'badge-' + item.bodyType">{{item.bodyType}}</span>
        <span v-if="!item.repoId" class="badge badge-light">Generic</span>
      </span>
  </div>
</template>
