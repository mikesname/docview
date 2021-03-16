<script>
export default {
  props: {
    disabled: Boolean,
    selected: Number,
    active: Boolean,
  },
};
</script>

<template functional>
  <button v-bind:disabled="props.disabled" v-on:click.prevent="listeners['validate']()" class="btn btn-sm btn-default">
    <i class="fa fa-fw" v-bind:class="{'fa-flag-o': !props.active, 'fa-circle-o-notch fa-spin': props.active}"/>
    <template v-if="props.selected > 0">
      Validate Selected ({{ props.selected }})
    </template>
    <template v-else>
      Validate All
    </template>
  </button>
</template>

