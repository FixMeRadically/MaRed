param([string]$Root, [string]$Folder='исправленные_работы')
$ErrorActionPreference='Stop'
$w=New-Object -ComObject Word.Application
$w.Visible=$false
$w.DisplayAlerts=0
try {
 foreach($f in (Get-ChildItem -LiteralPath (Join-Path $Root $Folder) -Filter '*.docx')) {
  $d=$null
  try {
   $d=$w.Documents.Open($f.FullName,$false,$false)
   foreach($t in $d.TablesOfContents){$t.Update()}
   $d.Fields.Update() | Out-Null
   $d.Repaginate()
   foreach($t in $d.TablesOfContents){$t.Update()}
   $d.Save()
   $pdf=[IO.Path]::ChangeExtension($f.FullName,'.pdf')
   $d.ExportAsFixedFormat($pdf,17)
   Write-Output ($f.Name+': '+$d.ComputeStatistics(2)+' pages')
  } finally {if($d){$d.Close(0)}}
 }
} finally {$w.Quit()}
