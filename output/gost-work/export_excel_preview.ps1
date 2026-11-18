param([string]$Root)
$ErrorActionPreference='Stop'
$excel=New-Object -ComObject Excel.Application
$excel.Visible=$false
$excel.DisplayAlerts=$false
try {
 $jobs=@(
  @{File='04_Оценки.xlsx';Sheet='Оценки';Range='A1:L34';Out='preview04'},
  @{File='05_Справочник.xlsx';Sheet='02_Раздел';Range='A1:J27';Out='preview05'}
 )
 foreach($job in $jobs){
  $book=$null
  try {
   $book=$excel.Workbooks.Open((Join-Path (Join-Path $Root 'финальные_работы') $job.File),0,$true)
   $sheet=$book.Worksheets.Item($job.Sheet)
   if($job.Out -eq 'preview04'){
    $series=$sheet.ChartObjects(1).Chart.SeriesCollection(1)
    $colors=@(0xB4771F,0x0E7FFF,0x2CA02C)
    for($i=1;$i -le 3;$i++){$series.Points($i).Format.Fill.ForeColor.RGB=$colors[$i-1]}
   }
   $setup=$sheet.PageSetup
   $setup.PrintArea=$job.Range
   $setup.Zoom=$false
   $setup.FitToPagesWide=1
   $setup.FitToPagesTall=1
   $setup.LeftMargin=0
   $setup.RightMargin=0
   $setup.TopMargin=0
   $setup.BottomMargin=0
   $setup.HeaderMargin=0
   $setup.FooterMargin=0
   $setup.PrintHeadings=$false
   $sheet.ExportAsFixedFormat(0,(Join-Path (Join-Path $Root 'qa-compact') ($job.Out+'.pdf')))
   Write-Output ($job.Out+' exported')
  } finally {if($book){$book.Close($false)}}
 }
} finally {$excel.Quit()}
