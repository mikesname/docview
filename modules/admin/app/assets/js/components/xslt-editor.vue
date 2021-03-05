<script>

// import CodeMirror from 'codemirror';

export default {
  props: {
    value: String,
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
    <textarea>{{value}}</textarea>
  </div>
</template>

