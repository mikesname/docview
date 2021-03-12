<script>

import MixinError from "./mixin-error";
import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/xml/xml';

import Vue from 'vue';

import _find from 'lodash/find';
import _isEqual from 'lodash/isEqual';
import _isArray from 'lodash/isArray';
import _fromPairs from 'lodash/fromPairs';


export default {
  mixins: [MixinError],
  props: {
    datasetId: String,
    fileStage: String,
    panelSize: Number,
    previewing: Object,
    config: Object,
    api: Object,
    maxSize: Number,
    validationResults: {
      type: Object,
      default: function () {
        return {}
      },
    },
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
      wrap: false,
      prettifying: false,
      prettified: false,
      showingError: false,
      worker: null,
      errored: false,
      errors: null,
      lineHandles: [],
      firstLoad: true,
    }
  },
  methods: {
    _isArray,

    prettifyXml: function (xml) {
      let parser = new DOMParser();
      let xmlDoc = parser.parseFromString(xml, 'application/xml');
      let xsltDoc = parser.parseFromString(`
        <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
          <xsl:template match="node()|@*">
            <xsl:copy>
              <xsl:apply-templates select="node()|@*"/>
            </xsl:copy>
          </xsl:template>
          <xsl:output indent="yes"/>
        </xsl:stylesheet>
      `, 'application/xml');

      let xsltProcessor = new XSLTProcessor();
      xsltProcessor.importStylesheet(xsltDoc);
      let resultDoc = xsltProcessor.transformToDocument(xmlDoc);
      return new XMLSerializer().serializeToString(resultDoc);
    },
    makePretty: function () {
      this.prettifying = true;
      this.previewData = this.prettifyXml(this.previewData);
      Vue.nextTick(() => {
        this.prettified = true
        this.prettifying = false;
      });
    },
    validate: function () {
      if (this.previewing === null) {
        return;
      }

      if (this.validationResults[this.previewing.eTag]) {
        this.errors = this.validationResults[this.previewing.eTag];
        this.updateErrors();
      } else {
        this.validating = true;
        let tagToKey = _fromPairs([[this.previewing.eTag, this.previewing.key]]);
        this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
            .then(errors => {
              let e = _find(errors, e => this.previewing.eTag === e.eTag);
              if (e) {
                this.errors = e.errors;
                this.updateErrors()
                this.$emit("validation-results", this.previewing.eTag, e.errors);
              }
            })
            .catch(error => this.showError("Error attempting validation", error))
            .finally(() => this.validating = false);
      }
    },
    updateErrors: function () {
      function makeWidget(err) {
        let widget = document.createElement("div");
        widget.style.color = "#822";
        widget.style.backgroundColor = "rgba(255,197,199,0.44)";
        widget.innerHTML = err.error;
        return widget;
      }

      function makeMarker(doc, err) {
        let marker = document.createElement("div");
        marker.style.color = "#822";
        marker.style.marginLeft = "3px";
        marker.className = "validation-error";
        marker.innerHTML = '<i class="fa fa-exclamation-circle"></i>';
        marker.querySelector("i").setAttribute("title", err.error);
        marker.addEventListener("click", function () {
          if (marker.widget) {
            marker.widget.clear();
            delete marker.widget;
          } else {
            marker.widget = doc.addLineWidget(err.line - 1, makeWidget(err));
          }
        });
        return marker;
      }

      // First clear any existing errors on the document...
      if (this.previewing && this.editor) {
        let doc = this.editor.getDoc();
        this.lineHandles.forEach(handle => doc.removeLineClass(handle, 'background'));
        doc.clearGutter('validation-errors');
        this.lineHandles = [];

        if (this.errors) {
          this.errors.forEach(e => {
            this.lineHandles.push(doc.addLineClass(e.line - 1, 'background', 'line-error'));
            doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(doc, e));
          });
        }
      }
    },
    setLoading: function () {
      this.loading = true;
      this.$emit("loading");
    },
    load: function () {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.api.fileUrls(this.datasetId, this.fileStage, [this.previewing.key])
          .then(data => this.worker.postMessage({
            type: 'preview',
            url: data[this.previewing.key],
            max: this.config.maxPreviewSize,
          }))
          .catch(error => this.showError('Unable to load preview URL', error))
          .finally(() => this.loading = false);
    },
    refresh: function () {
      this.editor.refresh();
    },
    receiveMessage: function (msg) {
      if (msg.data.error) {
        let errObj = msg.data.error;
        this.previewData = errObj.line
            ? "Error at line " + errObj.line + ": " + errObj.error
            : "Error: " + msg.data.error.error;
        this.editor.setOption("mode", null);
        this.loading = false;
        this.showingError = true;
      } else if (msg.data.init) {
        if (this.editor) {
          this.validate();
          this.editor.setOption("mode", "xml");

          // On the first load of a given file scroll back
          // to the beginning...
          if (this.firstLoad) {
            this.editor.scrollTo(0, 0);
            this.firstLoad = false;
          }
        }
        // Stop loading indicator when first data arrives
        this.loading = false;
        this.showingError = false;
        this.previewTruncated = false;
        this.previewData = msg.data.text;
      } else {
        this.previewData += msg.data.text;
        this.previewTruncated = msg.data.truncated;
      }
      if (msg.data.done) {
        this.$emit("loaded");
      }
    }
  },
  watch: {
    previewData: function (newValue, oldValue) {
      let editorValue = this.editor.getValue();
      if (newValue !== editorValue) {
        let scrollInfo = this.editor.getScrollInfo();
        this.editor.setValue(newValue);
        this.editor.scrollTo(scrollInfo.left, scrollInfo.top);
      }
      // FIXME: why is this only needed some times, e.g for convert
      // previews but not file previews?
      if (newValue !== oldValue) {
        if (!this.prettifying) {
          this.prettified = false;
        }
        this.refresh();
      }
    },
    previewing: function (newValue, oldValue) {
      if (!_isEqual(newValue, oldValue)) {
        this.load();
      }
      if (newValue && oldValue && newValue.key !== oldValue.key) {
        this.firstLoad = true;
      }
    },
    panelSize: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.refresh();
      }
    },
  },
  created: function () {
    this.worker = new Worker(this.config.previewLoader);
    this.worker.onmessage = this.receiveMessage;
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineWrapping: this.wrap,
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", () => this.updateErrors());

    this.load();
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
    if (this.worker) {
      this.worker.terminate();
    }
  },
}
</script>

<template>
  <div class="preview-container">
    <textarea>{{previewData}}</textarea>
    <div class="validation-loading-indicator" v-if="validating">
      <i class="fa fa-circle"></i>
    </div>
    <div class="valid-indicator" title="No errors detected"
         v-if="!validating && previewing && (_isArray(errors) && errors.length === 0)">
      <i class="fa fa-check"></i>
    </div>
    <div class="preview-loading-indicator" v-if="loading">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
    <button v-else-if="!showingError"
            v-on:click="makePretty"
            v-bind:class="{'active': !prettified}"
            v-bind:disabled="previewTruncated || prettified"
            class="pretty-xml btn btn-sm"
            title="Apply code formatting...">
      <i class="fa fa-code"></i>
    </button>
  </div>
</template>
