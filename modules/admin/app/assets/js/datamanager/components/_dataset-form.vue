<script>

import ModalWindow from './_modal-window';
import DAO from '../dao';

export default {
  components: {ModalWindow},
  props: {
    api: DAO,
    config: Object,
    info: Object,
  },
  data: function() {
    return {
      id: this.info ? this.info.id : null,
      name: this.info ? this.info.name : null,
      src: this.info ? this.info.src : null,
      fonds: this.info ? this.info.fonds : null,
      sync: this.info ? this.info.sync : false,
      notes: this.info ? this.info.notes : null,
      error: null,
      saving: false,
      deleting: false,
      showRemoveDialog: false,
      showAdvanced: false,
    }
  },
  methods: {
    save: function() {
      this.saving = true;

      let data = {
        id: this.id,
        name: this.name,
        src: this.src,
        fonds: this.fonds,
        sync: this.sync,
        notes: this.notes,
      };

      let op = this.info !== null
          ? this.api.updateDataset(this.id, data).then(ds => this.$emit('saved-dataset', ds))
          : this.api.createDataset(data).then(ds => {
            this.$emit('saved-dataset', ds);
            this.$emit('close');
          });

      op.catch(error => {
        if (error.response && error.response.data && error.response.data.error) {
          this.error = error.response.data.error;
        } else {
          throw error;
        }
      })
          .finally(() => this.saving = false);
    },
    remove: function() {
      this.deleting = true;
      this.showRemoveDialog = false;
      this.api.deleteDataset(this.id)
          .then(() => {
                this.$emit('deleted-dataset');
                this.$emit('close');
              }
          )
          .catch(error => this.error = error)
          .finally(() => this.deleting = false);
    },
  },
  watch: {
    fonds: function(newValue) {
      if (!newValue) {
        this.fonds = null;
        this.sync = false;
      }
    }
  },
  computed: {
    isValidConfig: function() {
      return this.src !== null
          && this.name !== null
          && this.id !== null
          && this.isValidFonds;
    },
    isValidIdentifier: function() {
      return !this.id || (this.id.match(/^[a-z0-9_]+$/) !== null && this.id.length <= 50);
    },
    isValidFonds: function() {
      return !this.fonds || this.fonds.match("^" + this.config.repoId + "-.+");
    },
    hasChanged: function() {
      return this.info === null || (
          this.info.name !== this.name
          || this.info.src !== this.src
          || this.info.notes !== this.notes
          || this.info.fonds !== this.fonds
          || Boolean(this.info.sync) !== Boolean(this.sync));
    }
  },

};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-if="info === null" v-slot:title>Create New Dataset...</template>
    <template v-else v-slot:title>Update Dataset: {{info.name}}</template>

    <modal-alert v-if="showRemoveDialog"
                 v-on:accept="remove"
                 v-on:close="showRemoveDialog = false"
                 v-bind:title="'Delete Dataset?'"
                 v-bind:accept="'Yes, delete it !'">
      <p>This will remove all files associated with the dataset and
        cannot be undone. Are you sure?</p>
    </modal-alert>
    <modal-alert v-if="error"
                 v-on:accept="error = null"
                 v-on:close="error = null"
                 v-bind:cancel="null"
                 v-bind:title="'Error saving dataset...'"
                 v-bind:cls="'warning'">
      <p>{{error}}</p>
    </modal-alert>
    <fieldset v-bind:disabled="deleting" class="options-form">
      <div class="form-group" v-if="info === null">
        <label class="form-label" for="dataset-id">
          Identifier
          <span class="required-input">*</span>
        </label>
        <input v-model="id"
               v-bind:class="{'is-invalid': !isValidIdentifier}"
               pattern="[a-z0-9_]+"
               maxlength="50"
               type="text"
               id="dataset-id"
               class="form-control"
               autofocus="autofocus"
               autocomplete="off"
               placeholder="(required)" />
        <div class="small form-text">
          Dataset identifiers must be at least 6 characters in length
          and can only contain lower case letters, numbers and underscores.
        </div>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-name">
          Name
          <span class="required-input">*</span>
        </label>
        <input type="text" v-model="name" id="dataset-name" class="form-control" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-src">
          Type
          <span class="required-input">*</span>
        </label>
        <select v-model="src" class="form-control" id="dataset-src">
          <option v-bind:value="null" disabled selected hidden>(required)</option>
          <option value="upload">Uploads</option>
          <option value="oaipmh">OAI-PMH Harvesting</option>
          <option value="rs">ResourceSync</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-fonds">
          Fonds ID
        </label>
        <input type="text" v-model="fonds" id="dataset-fonds" class="form-control" placeholder="(optional)"/>
      </div>
      <div class="form-group form-check">
        <input v-bind:disabled="!(this.fonds && this.isValidFonds)" v-model="sync" class="form-check-input" id="opt-sync" type="checkbox"/>
        <label class="form-check-label" for="opt-sync">
          Synchronise fonds with dataset
        </label>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-notes">Notes</label>
        <textarea rows="4" v-model="notes" id="dataset-notes" class="form-control" placeholder="(optional)"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-if="info"
              v-bind:disabled="saving || deleting"
              v-on:click="showRemoveDialog = true" class="btn btn-danger" id="delete-dataset" tabindex="-1">
        <i v-if="deleting" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-trash-o"></i>
        Delete Dataset
      </button>
      <button v-bind:disabled="saving || deleting || !hasChanged || !(isValidConfig && isValidIdentifier)"
              v-on:click="save" type="button" class="btn btn-secondary">
        <i v-if="saving" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-save"></i>
        <template v-if="info">Save Dataset</template>
        <template v-else>Create Dataset</template>
      </button>
    </template>
  </modal-window>
</template>

