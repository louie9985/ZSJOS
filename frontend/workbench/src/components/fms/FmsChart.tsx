import { useEffect, useRef } from 'react'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

interface FmsChartProps {
  options: EChartsOption
  height?: number
  loading?: boolean
}

/** ECharts 薄封装：按需注册折线/饼图，不引入全量 echarts */
export default function FmsChart({ options, height = 360, loading = false }: FmsChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | undefined>(undefined)

  useEffect(() => {
    if (!containerRef.current) return
    if (!chartRef.current) {
      chartRef.current = echarts.init(containerRef.current)
    }
    chartRef.current.setOption(options, { notMerge: true })
  }, [options])

  // 容器尺寸变化时 resize
  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const observer = new ResizeObserver(() => chartRef.current?.resize())
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (loading) {
      chartRef.current?.showLoading()
    } else {
      chartRef.current?.hideLoading()
    }
  }, [loading])

  useEffect(() => () => chartRef.current?.dispose(), [])

  return <div ref={containerRef} style={{ width: '100%', height }} />
}
