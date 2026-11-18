param([string]$Root)
$ErrorActionPreference='Stop'
$w=New-Object -ComObject Word.Application
$w.Visible=$false;$w.DisplayAlerts=0
try{
 foreach($name in @('02_Семейное_дерево.docx','01_План_квартиры.docx')){
  $d=$w.Documents.Open((Join-Path (Join-Path $Root 'Связанные_работы') $name),$false,$true)
  try{
   $r=$d.Content.Duplicate
   if($name -eq '01_План_квартиры.docx'){$r=$d.Sections.Item(1).Range.Duplicate}
   $r.End=$r.End-1
   [byte[]]$bits=$r.EnhMetaFileBits
   [IO.File]::WriteAllBytes((Join-Path (Join-Path $Root 'qa') ($name+'.emf')),$bits)
   Write-Output ($name+' exported '+$bits.Length)
  }finally{$d.Close(0)}
 }
}finally{$w.Quit()}
