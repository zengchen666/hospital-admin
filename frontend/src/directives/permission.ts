import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-perm 权限指令。
 *
 * <p>用法：{@code <el-button v-perm="'patient:add'">建档</el-button>}</p>
 *
 * <p><b>它只是「不显示」，不是安全边界</b>：后端 {@code @PreAuthorize} 才是真正的拦截。
 * 没有权限的人就算手动发请求也会被 40301 挡回来，这里只是别让他看到一个点了必错的按钮。</p>
 *
 * <p>实现上直接移除 DOM 节点而不是 display:none ——
 * 后者在 DevTools 里改一下样式就能点，且会留下奇怪的空白。</p>
 */
function apply(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const store = useUserStore()
  if (!store.hasPerm(binding.value)) {
    el.parentNode?.removeChild(el)
  }
}

export const permission: Directive<HTMLElement, string | string[]> = {
  mounted: apply,
  updated: apply,
}

export default permission
