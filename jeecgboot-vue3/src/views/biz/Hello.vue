<template>
  <div class="biz-hello">
    <div class="title">Biz 示例</div>
    <div class="block">
      <button class="btn" @click="loadTest">调用 /hello/test</button>
      <div class="result">{{ testMsg }}</div>
    </div>
    <div class="block">
      <button class="btn" @click="loadOk">调用 /hello/ok</button>
      <div class="result">{{ okMsg }}</div>
    </div>
  </div>
  </template>

<script setup lang="ts">
import { ref } from 'vue';
import { helloTest, helloOk } from './hello.api';

const testMsg = ref('');
const okMsg = ref('');

async function loadTest() {
  try {
    testMsg.value = await helloTest();
  } catch (e) {
    testMsg.value = '';
  }
}

async function loadOk() {
  try {
    okMsg.value = await helloOk();
  } catch (e) {
    okMsg.value = '';
  }
}
</script>

<style scoped>
.biz-hello { padding: 16px; }
.title { font-weight: 600; margin-bottom: 12px; }
.block { margin-bottom: 12px; }
.btn { padding: 6px 12px; border: 1px solid #ccc; border-radius: 4px; background: #fff; cursor: pointer; }
.result { margin-top: 8px; color: #333; }
</style>

