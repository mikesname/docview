<script>
export default {
  props: {
    item: Object,
  },
}
</script>

<template functional>
  <div v-on:dblclick="listeners.edit()"
       class="list-group-item transformation-item list-group-item-action">
    <h4 class="transformation-item-name">
      {{ props.item.name }}
      <span class="transformation-item-comments" v-bind:title="props.item.comments">{{ props.item.comments }}</span>
    </h4>
    <button class="transformation-item-edit btn btn-sm btn-default" v-on:click="listeners.edit()">
      <i class="fa fa-edit"></i>
    </button>
    <span class="transformation-item-meta">
        <span class="badge badge-pill" v-bind:class="'badge-' + props.item.bodyType">{{ props.item.bodyType }}</span>
        <span v-if="!props.item.repoId" class="badge badge-light">Generic</span>
      </span>
  </div>
</template>
