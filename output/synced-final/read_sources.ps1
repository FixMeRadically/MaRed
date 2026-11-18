param([string]$Root)
$ErrorActionPreference='Stop'
$w=New-Object -ComObject Word.Application
$w.Visible=$false
$w.DisplayAlerts=0
try{
 foreach($name in @('02_Семейное_дерево.docx','01_План_квартиры.docx','02_Отчёт.docx')){
  $d=$null
  try{
   $d=$w.Documents.Open((Join-Path (Join-Path $Root 'input\ФИНАЛ') $name),$false,$true)
   $d.ExportAsFixedFormat((Join-Path (Join-Path $Root 'qa') ([IO.Path]::GetFileNameWithoutExtension($name)+'.pdf')),17)
   Write-Output ($name+': '+$d.ComputeStatistics(2)+' pages')
  }finally{if($d){$d.Close(0)}}
 }
}finally{$w.Quit()}
