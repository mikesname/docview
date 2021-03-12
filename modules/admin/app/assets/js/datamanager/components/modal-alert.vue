<script>
export default {
  props: {
    title: String,
    cls: {
      type: String,
      default: 'danger'
    },
    accept: {
      type: String,
      default: "Okay"
    },
    cancel: {
      type: String,
      default: "Cancel"
    }
  },
  mounted() {
    this.$el.querySelector("button.close").focus();
  }
}
</script>

<template>
  <div v-bind:class="cls" class="modal modal-alert" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-sm" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">{{ title }}</h5>
          <button type="button" class="close" aria-label="Close" v-on:click="$emit('close')" tabindex="-1">
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
        <div class="modal-body">
          <slot></slot>
        </div>
        <div class="modal-footer">
          <button v-if="cancel" type="button" class="btn" v-on:click="$emit('close')" autofocus>{{ cancel }}</button>
          <button v-if="accept" type="button" class="btn" v-bind:class="'btn-' + cls" v-on:click="$emit('accept')">
            {{ accept }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
