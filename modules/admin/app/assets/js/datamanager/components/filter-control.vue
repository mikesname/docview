<script>
export default {
  props: {
    filter: Object
  },
};
</script>

<template>
  <div class="filter-control">
    <label class="sr-only">Filter files</label>
    <input class="filter-input form-control form-control-sm" type="text" v-model.trim="filter.value"
           placeholder="Filter files..." v-on:keyup="$emit('filter')"/>
    <i class="filtering-indicator fa fa-circle-o-notch fa-fw fa-spin" v-if="filter.active"/>
    <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="$emit('clear')" v-else-if="filter.value"/>
  </div>
</template>

