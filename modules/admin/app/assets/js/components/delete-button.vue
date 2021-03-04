<script>
export default {
  props: {
    disabled: Boolean,
    selected: Number,
    active: Boolean,
  },
};
</script>

<template>
  <button v-bind:disabled="disabled" v-on:click.prevent="$emit('delete')" class="btn btn-sm btn-default">
    <i class="fa fa-fw" v-bind:class="{'fa-trash-o': !active, 'fa-circle-o-notch fa-spin': active}"/>
    <template v-if="selected > 0">
      Delete Selected ({{selected}})
    </template>
    <template v-else>
      Delete All
    </template>
  </button>
</template>

