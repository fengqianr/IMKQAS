<template>
  <MainLayout :menus="MENUS.patient" brand="Clinical Precision RAG" variant="portal">
    <slot />
  </MainLayout>
</template>

<script setup lang="ts">
// 患者门户布局：复用泛化 MainLayout 骨架，注入患者端菜单
import MainLayout from './MainLayout.vue'
import { MENUS } from '@/config/menus'
</script>
