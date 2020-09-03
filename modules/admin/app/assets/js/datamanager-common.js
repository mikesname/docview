"use strict";

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(() => sequential(func, arr, index + 1));
}

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function (bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("prettyDate", function (time) {
  let f = time => {
    let m = moment(time);
    return m.isValid() ? m.fromNow() : "";
  };
  return _.memoize(f)(time);
});

let previewMixin = {
  data: function() {
    return {
      tab: 'preview'
    }
  },
  methods: {
    showPreview: function (key) {
      this.previewing = key;
      this.tab = 'preview';
    },
  }
};

let twoPanelMixin = {
  data: function() {
    return {
      panelSize: null,
    }
  },
  methods: {
    setPanelSize: function (arbitrarySize) {
      this.panelSize = arbitrarySize;
    }
  }
};

let validatorMixin = {
  data: function() {
    return {
      validating: {},
      validationResults: {},
    }
  },
  methods: {
    validateFiles: function (keys) {
      keys.forEach(key => this.$set(this.validating, key, true));
      keys.forEach(key => this.$delete(this.validationResults, key));
      DAO.validateFiles(this.fileStage, keys).then(errs => {
        this.tab = 'validation';
        keys.forEach(key => {
          this.$set(this.validationResults, key, errs[key] ? errs[key] : []);
          this.$delete(this.validating, key);
        });
      });
    },

  },
  computed: {
    validationLog: function () {
      let log = [];
      this.files.forEach(file => {
        let key = file.key;
        let errs = this.validationResults[key];
        if (errs) {
          let cls = errs.length === 0 ? "text-success" : "text-danger";
          log.push('<span class="' + cls + '">' + key + '</span>' + ":" + (errs.length === 0 ? " ✓" : ""));
          errs.forEach(err => {
            log.push("    " + err.line + "/" + err.pos + " - " + err.error);
          });
        }
      });
      return log;
    }
  }
};

let previewPanelMixin = {
  props: {
    fileStage: String,
    panelSize: Number,
    previewing: String,
    errors: null,
    config: Object,
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
      wrap: true,
      prettifying: false,
      prettified: false,
    }
  },
  methods: {
    prettifyXml: function(xml) {
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
    makePretty: function() {
      this.prettifying = true;
      try {
        this.previewData = this.prettifyXml(this.previewData);
        this.prettified = true;
      } finally {
        this.prettifying = false;
      }
    },
    spawnLoader: function() {
      let self = this;
      self.loading = true;
      let worker = new Worker(self.config.previewLoader);
      worker.onmessage = e => {
        if (e.data.init) {
          if (self.editor) {
            self.validate();
            self.editor.scrollTo(0, 0);
          }
          self.loading = false;
          self.previewData = e.data.text;
        } else {
          self.previewData += e.data.text;
        }
      };
      return worker;
    },
    validate: function () {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      self.validating = true;
      DAO.validateFiles(this.fileStage, [self.previewing]).then(errs => {
        this.$set(this.errors, self.previewing, errs[self.previewing]);
        this.updateErrors();
        this.validating = false;
      });
    },
    updateErrors: function () {
      if (this.errors[this.previewing] && this.editor) {
        let doc = this.editor.getDoc();

        function makeMarker(err) {
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

        function makeWidget(err) {
          let widget = document.createElement("div");
          widget.style.color = "#822";
          widget.style.backgroundColor = "rgba(255,197,199,0.44)";
          widget.innerHTML = err.error;
          return widget;
        }

        this.errors[this.previewing].forEach(e => {
          doc.addLineClass(e.line - 1, 'background', 'line-error');
          doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(e));
        });
      }

    },
    load: function () {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      self.loading = true;
      DAO.fileUrls(self.fileStage, [self.previewing]).then(data => {
        let worker = self.spawnLoader();
        worker.postMessage({type: 'preview', url: data[self.previewing]});
      });
    },
    refresh: function() {
      this.editor.refresh();
    },
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
      if (newValue !== null && newValue !== oldValue) {
        this.load();
      }
    },
    panelSize: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.refresh();
      }
    }
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      // lineWrapping: true,
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", () => {
      console.log("Editor refresh!");
      this.updateErrors();
    });

    this.load();
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
  template: `
    <div class="preview-container">
      <textarea>{{previewData}}</textarea>
      <div class="validation-loading-indicator" v-if="validating">
        <i class="fa fa-circle"></i>
      </div>
      <div class="valid-indicator" title="No errors detected"
           v-if="!validating && errors[previewing] && errors[previewing].length === 0">
        <i class="fa fa-check"></i>
      </div>
      <div class="preview-loading-indicator" v-if="loading">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
      <button class="pretty-xml btn btn-sm"
              title="Apply code formatting..."
              v-else
              v-bind:class="{'active': !prettified}"
              v-on:click="makePretty" 
              v-bind:disabled="previewTruncated || prettified">
        <i class="fa fa-code"></i>
      </button>
    </div>
  `
};

Vue.component("file-picker-suggestion", {
  props: {selected: Boolean, item: Object,},
  template: `
    <div @click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
        {{ item.key }} 
    </div>
  `
});

Vue.component("file-picker", {
  props: {type: String, disabled: Boolean},
  data: function () {
    return {
      text: "",
      input: "",
      selectedIdx: -1,
      suggestions: [],
      loading: false,
      item: null,
    }
  },
  methods: {
    search: function () {
      this.loading = true;
      this.text = this.input;
      let self = this;
      function list() {
        DAO.listFiles(self.type, self.text).then(data => {
          self.loading = false;
          self.suggestions = data.files;
        });
      }
      _.debounce(list, 300)();
    },
    setItem: function (item) {
      this.item = item;
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
      this.setItemFromSelection();
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
      this.setItemFromSelection();
    },
    setAndChooseItem: function (item) {
      this.setItem(item);
      this.accept();
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
        len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.item = null;
      }
    },
    accept: function () {
      if (this.item) {
        this.$emit("item-accepted", this.item);
        this.input = this.item.key;
        this.cancelComplete();
        this.text = "";
      }
    },
    cancelComplete: function () {
      this.suggestions = [];
      this.selectedIdx = -1;
      this.item = null;
    }
  },
  template: `
    <div class="file-picker">
      <label class="control-label sr-only">File:</label>
      <input class="form-control" type="text" placeholder="Select file to preview"
        v-bind:disabled="disabled"
        v-model.trim="input" 
        v-on:input="search"
        v-on:focus="search"
        v-on:keydown.up="selectPrev"
        v-on:keydown.down="selectNext"
        v-on:keydown.enter="accept"
        v-on:keydown.esc="cancelComplete"/>
      <div class="dropdown-list" v-if="suggestions.length">
        <div class="file-picker-suggestions">
          <file-picker-suggestion
              v-for="(suggestion, i) in suggestions"
              v-bind:class="{selected: i === selectedIdx}"
              v-bind:key="suggestion.key"
              v-bind:item="suggestion"
              v-bind:selected="i === selectedIdx"
              v-on:selected="setAndChooseItem"/>
        </div>
      </div>
    </div>
  `
});

Vue.component("convert-preview", {
  mixins: [previewPanelMixin],
  props: {
    mappings: Array,
    trigger: String,
  },
  methods: {
    validate: function() {
      // FIXME: not yet supported
    },
    load: function() {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      let worker = self.spawnLoader();
      worker.postMessage({
        type: 'convert-preview',
        url: DAO.convertFileUrl(self.fileStage, self.previewing),
        src: [],
        mappings: self.mappings,
      });
    }
  },
  watch: {
    trigger: function(newObj, oldObj) {
      console.log("trigger");
      this.load();
    },
    config: function(newConfig, oldConfig) {
      if (newConfig !== oldConfig && newConfig !== null) {
        console.log("Refresh convert preview...");
        this.load();
      }
    }
  }
})

Vue.component("preview", {
  mixins: [previewPanelMixin]
});


Vue.component("filter-control", {
  props: {
    filter: Object
  },
  template: `
    <div class="filter-control">
      <label class="sr-only">Filter files</label>
      <input class="filter-input form-control form-control-sm" type="text" v-model.trim="filter.value"
             placeholder="Filter files..." v-on:keyup="$emit('filter')"/>
      <i class="filtering-indicator fa fa-circle-o-notch fa-fw fa-spin" v-if="filter.active"/>
      <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="$emit('clear')" v-else-if="filter.value"/>
    </div>
  `
});

Vue.component("log-window", {
  props: {
    log: Array,
  },
  updated: function () {
    this.$el.scrollTop = this.$el.scrollHeight;
  },
  template: `
    <pre v-if="log.length > 0"><template v-for="msg in log"><span v-html="msg"></span><br/></template></pre>
  `
});

Vue.component("drag-handle", {
  props: {
    ns: String,
    p2: Element,
    container: Element,
  },
  data: function () {
    return {
      offset: 0,
    }
  },

  methods: {
    move: function (evt) {
      // Calculate the height of the topmost panel in percent.
      let maxY = this.container.offsetTop + this.container.offsetHeight;
      let topY = this.container.offsetTop;
      let posY = evt.clientY - this.offset;

      let pxHeight = Math.min(maxY, Math.max(0, posY - topY));
      let percentHeight = pxHeight / this.container.offsetHeight * 100;

      // Now convert to the height of the lower panel.
      let perc = 100 - percentHeight;
      this.p2.style.flexBasis = perc + "%";
    },
    startDrag: function (evt) {
      console.log("Bind resize", new Date());
      let us = this.container.style.userSelect;
      let cursor = this.container.style.cursor;
      this.offset = evt.clientY - this.$el.offsetTop;
      this.container.addEventListener("mousemove", this.move);
      this.container.style.userSelect = "none";
      this.container.style.cursor = "ns-resize";
      window.addEventListener("mouseup", () => {
        console.log("Stop resize");
        this.offset = 0;
        this.$emit("resize", this.p2.clientHeight);
        this.container.style.userSelect = us;
        this.container.style.cursor = cursor;
        this.container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
  template: `
    <div v-bind:id="ns + '-drag-handle'" class="drag-handle" v-on:mousedown="startDrag"></div>
  `
});


