<script lang="ts">

export default {
  props: {
    selected: Boolean,
    item: Object,
  },
  filters: { decodeURI }
};
</script>

<template>
  <div @click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
    {{ item.key|decodeURI }}
  </div>
</template>

