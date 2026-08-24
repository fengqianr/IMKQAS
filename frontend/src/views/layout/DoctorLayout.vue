<template>
  <MainLayout :menus="MENUS.doctor" brand="Clinical Precision RAG" variant="clinical">
    <slot />
  </MainLayout>
</template>

<script setup lang="ts">
// 医生临床工作台布局：复用泛化 MainLayout 骨架，注入医生端菜单
import MainLayout from './MainLayout.vue'
import { MENUS } from '@/config/menus'
</script>
