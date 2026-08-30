<template>
  <div class="login-page">
    <div class="login-art">
      <div class="art-glow"></div>
      <div class="art-copy"><span>奥能电源员工平台</span>
        <h1>让知识<br/>流动起来</h1>
        <p>连接每一位员工，让信息成为组织的共同语言。</p></div>
    </div>
    <div class="login-form">
      <div class="login-logo"><span>奥</span><b>奥能电源</b></div>
      <h2>欢迎回来</h2>
      <p class="login-sub">登录员工数字化平台，开始高效工作</p>
      <el-form @submit.prevent="login">
        <el-form-item>
          <el-input v-model="username" size="large" placeholder="员工账号 / 邮箱"/>
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" size="large" type="password" show-password placeholder="密码"/>
        </el-form-item>
        <div class="form-row">
          <el-checkbox v-model="remember">记住我</el-checkbox>
          <a>忘记密码？</a></div>
        <el-button type="primary" native-type="submit" size="large" class="login-btn" :loading="loading">登录
        </el-button>
      </el-form>
      <p class="login-tip">首次登录请使用企业统一身份认证</p></div>
  </div>
</template>
<script setup lang="ts">import {ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage} from 'element-plus';
import {authApi} from '../../api';

const router = useRouter();
const username = ref('admin');
const password = ref('123456');
const remember = ref(true);
const loading = ref(false);

async function login() {
  loading.value = true;
  try {
    const {data} = await authApi.login({username: username.value, password: password.value});
    localStorage.setItem('rag_token', data.token)
  } catch {
    ElMessage.error('登录失败，请检查账号和密码');
    return
  } finally {
    loading.value = false
  }
  ElMessage.success('登录成功');
  router.push('/dashboard')
}</script>
