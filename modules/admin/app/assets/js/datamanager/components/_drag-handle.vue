<script lang="ts">

export default {
  props: {
    ns: String,
    p2: Function,
    container: Function,
  },
  data: function (): Object {
    return {
      offset: 0,
    }
  },

  methods: {
    move: function (evt) {
      // Calculate the height of the topmost panel in percent.
      let container = this.container(),
          p2 = this.p2();

      let maxY = container.offsetTop + container.offsetHeight;
      let topY = container.offsetTop;
      let posY = evt.clientY - this.offset;

      let pxHeight = Math.min(maxY, Math.max(0, posY - topY));
      let percentHeight = pxHeight / container.offsetHeight * 100;

      // Now convert to the height of the lower panel.
      let perc = 100 - percentHeight;
      p2.style.flexBasis = perc + "%";
    },
    startDrag: function (evt) {
      // Calculate the height of the topmost panel in percent.
      let container = this.container(),
          p2 = this.p2();

      let us = container.style.userSelect;
      let cursor = container.style.cursor;
      this.offset = evt.clientY - this.$el.offsetTop;
      container.addEventListener("mousemove", this.move);
      container.style.userSelect = "none";
      container.style.cursor = "ns-resize";
      window.addEventListener("mouseup", () => {
        console.debug("Stop resize");
        this.offset = 0;
        this.$emit("resize", p2.clientHeight);
        container.style.userSelect = us;
        container.style.cursor = cursor;
        container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
};
</script>

<template>
  <div v-bind:id="ns + '-drag-handle'" class="drag-handle" v-on:mousedown="startDrag"></div>
</template>

