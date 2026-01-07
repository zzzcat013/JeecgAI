<template>
  <div class="type-cascader">
    <a-space>
      <a-select v-model:value="l1" style="min-width: 160px" placeholder="一级类型" @change="onL1Change">
        <a-select-option v-for="i in level1" :key="i.code" :value="i.code">{{ i.code }} - {{ i.name }}</a-select-option>
      </a-select>
      <a-select v-model:value="l2" style="min-width: 200px" placeholder="二级类型" @change="onL2Change">
        <a-select-option v-for="i in level2" :key="i.code" :value="i.code">{{ i.code }} - {{ i.name }}</a-select-option>
      </a-select>
      <a-select v-model:value="l3" style="min-width: 240px" placeholder="三级类型" @change="emitChange">
        <a-select-option v-for="i in level3" :key="i.code" :value="i.code">{{ i.code }} - {{ i.name }}</a-select-option>
      </a-select>
    </a-space>
  </div>
  </template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { listTypes, type DocTypeItem } from '../api/doctype.api';

const props = defineProps<{ modelValue?: { l1?: string; l2?: string; l3?: string } }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: { l1?: string; l2?: string; l3?: string }): void; (e: 'change', v: { l1?: string; l2?: string; l3?: string }): void }>();

const l1 = ref<string | undefined>(props.modelValue?.l1);
const l2 = ref<string | undefined>(props.modelValue?.l2);
const l3 = ref<string | undefined>(props.modelValue?.l3);

const level1 = ref<DocTypeItem[]>([]);
const level2 = ref<DocTypeItem[]>([]);
const level3 = ref<DocTypeItem[]>([]);

async function loadLevel1() {
  try {
    level1.value = await listTypes({ level: 1 });
  } catch (e) {
    level1.value = [];
  }
}
async function loadLevel2() {
  try {
    if (!l1.value) { level2.value = []; return; }
    level2.value = await listTypes({ level: 2, parentCode: l1.value });
  } catch (e) {
    level2.value = [];
  }
}
async function loadLevel3() {
  try {
    if (!l2.value) { level3.value = []; return; }
    level3.value = await listTypes({ level: 3, parentCode: l2.value });
  } catch (e) {
    level3.value = [];
  }
}

function onL1Change() { l2.value = undefined; l3.value = undefined; loadLevel2(); emitChange(); }
function onL2Change() { l3.value = undefined; loadLevel3(); emitChange(); }
function emitChange() {
  const v = { l1: l1.value, l2: l2.value, l3: l3.value };
  emit('update:modelValue', v);
  emit('change', v);
}

onMounted(async () => { await loadLevel1(); if (l1.value) await loadLevel2(); if (l2.value) await loadLevel3(); });
watch(() => props.modelValue, (val) => { l1.value = val?.l1; l2.value = val?.l2; l3.value = val?.l3; });
</script>

<style scoped>
.type-cascader { display: inline-block; }
</style>
