<template>
  <MainLayout :menus="MENUS.admin" brand="Clinical Precision RAG" variant="admin">
    <slot />
  </MainLayout>
</template>

<script setup lang="ts">
// 管理后台布局：复用泛化 MainLayout 骨架，注入管理员端菜单
import MainLayout from './MainLayout.vue'
import { MENUS } from '@/config/menus'
</script>
