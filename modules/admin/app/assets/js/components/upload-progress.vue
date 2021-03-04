<script>
export default {
  props: {
    uploading: Array,
  },
  data: function() {
    return {
      showProgress: true,
    };
  },
};
</script>

<template>
  <div v-if="uploading.length > 0" class="upload-progress-container">
    <div class="upload-progress-title">
      <div v-on:click.prevent="showProgress = !showProgress" class="close">
        <i class="fa fa-window-restore"></i>
      </div>
    </div>
    <div v-if="showProgress" class="upload-progress">
      <div v-for="job in uploading" v-bind:key="job.spec.name" class="progress-container">
        <div class="progress">
          <div class="progress-bar progress-bar-striped progress-bar-animated"
               role="progressbar"
               v-bind:aria-valuemax="100"
               v-bind:aria-valuemin="0"
               v-bind:aria-valuenow="job.progress"
               v-bind:style="'width: ' + job.progress + '%'">
            {{ job.spec.name}}
          </div>
        </div>
        <button class="btn btn-sm btn-default cancel-button" v-on:click.prevent="$emit('finish-item', job.spec)">
          <i class="fa fa-fw fa-times-circle"/>
        </button>
      </div>
    </div>
    <div class="upload-progress-controls">
      <div class="btn btn-sm btn-default" v-on:click="$emit('cancel-upload')">
        <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Cancel Uploads
      </div>
    </div>
  </div>
</template>

