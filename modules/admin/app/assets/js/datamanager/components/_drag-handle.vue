<script lang="ts">

export default {
  props: {
    ns: String,
    p2: Element,
    container: Element,
  },
  data: function (): Object {
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
      let us = this.container.style.userSelect;
      let cursor = this.container.style.cursor;
      this.offset = evt.clientY - this.$el.offsetTop;
      this.container.addEventListener("mousemove", this.move);
      this.container.style.userSelect = "none";
      this.container.style.cursor = "ns-resize";
      window.addEventListener("mouseup", () => {
        console.debug("Stop resize");
        this.offset = 0;
        this.$emit("resize", this.p2.clientHeight);
        this.container.style.userSelect = us;
        this.container.style.cursor = cursor;
        this.container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
};
</script>

<template>
  <div v-bind:id="ns + '-drag-handle'" class="drag-handle" v-on:mousedown="startDrag"></div>
</template>

