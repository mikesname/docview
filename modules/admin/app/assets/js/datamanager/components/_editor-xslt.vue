<script lang="ts">

import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/xml/xml';

export default {
  props: {
    value: String,
    resize: {
      // this value provides a trigger to refresh the editor when size changes
      // it does not reflect the actual value of the panel
      type: Number,
    },
  },
  watch: {
    resize: function() {
      this.editor.refresh();
    }
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: false,
      readOnly: false,
    });
    this.editor.on("change", () => {
      this.$emit('input', this.editor.getValue());
    });
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
};
</script>

<template>
  <div class="xslt-editor">
    <textarea>{{ value }}</textarea>
  </div>
</template>

