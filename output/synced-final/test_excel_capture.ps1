param([string]$Root)
$ErrorActionPreference='Stop'
$excel=New-Object -ComObject Excel.Application
$excel.Visible=$false;$excel.DisplayAlerts=$false
try{
 foreach($job in @(@{File='Копия 04_Оценки.xlsx';Sheet='Оценки';Range='A1:L34';Out='test04'},@{File='Копия 05_Справочник.xlsx';Sheet='Содержание';Range='A1:J19';Out='test05contents'},@{File='Копия 05_Справочник.xlsx';Sheet='02_Раздел';Range='A1:J27';Out='test05section'})){
  $book=$excel.Workbooks.Open((Join-Path (Join-Path $Root 'Связанные_работы') $job.File),0,$true)
  try{
   $s=$book.Worksheets.Item($job.Sheet)
   $s.Activate()
   $r=$s.Range($job.Range)
   $r.CopyPicture(1,2)
   $chart=$s.ChartObjects().Add(0,0,$r.Width,$r.Height)
   $chart.Activate()
   $chart.Chart.Paste()
   $path=Join-Path (Join-Path $Root 'qa') ($job.Out+'.png')
   $ok=$chart.Chart.Export($path,'PNG')
   Write-Output ($job.Out+' -> '+$ok)
   $chart.Delete()
  }finally{$book.Close($false)}
 }
}finally{$excel.Quit()}
