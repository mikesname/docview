<script>
export default {
  props: {
    selected: Boolean,
    item: Object,
  },
};
</script>

<template>
  <div @click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
    {{ item.key|decodeUri }}
  </div>
</template>

