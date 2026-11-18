param([string]$PdfFolder='')
$ErrorActionPreference='Stop'
$Root=$PSScriptRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.Drawing
$W='http://schemas.openxmlformats.org/wordprocessingml/2006/main'
$A='http://schemas.openxmlformats.org/drawingml/2006/main'
$R='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
$P='http://schemas.openxmlformats.org/package/2006/relationships'
$jobs=@(
 @{Report='01_Отчёт.docx';Image='01_План.png';Index=2;Height=95},
 @{Report='02_Отчёт.docx';Image='02_Древо.png';Index=2;Height=80},
 @{Report='04_Отчёт.docx';Image='04_Оценки.png';Index=2;Height=80},
 @{Report='05_Отчёт.docx';Image='05_Содержание.png';Index=2;Height=85},
 @{Report='05_Отчёт.docx';Image='05_Раздел.png';Index=3;Height=110}
)
function Read-Part($zip,$name){
 $s=$zip.GetEntry($name).Open();$r=[IO.StreamReader]::new($s)
 try{return $r.ReadToEnd()}finally{$r.Dispose();$s.Dispose()}
}
function Write-Part($zip,$name,[byte[]]$bytes){
 $old=$zip.GetEntry($name)
 if($old){$old.Delete()}
 $entry=$zip.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
 $s=$entry.Open()
 try{$s.Write($bytes,0,$bytes.Length)}finally{$s.Dispose()}
}
function Write-Xml($zip,$name,$xml){
 $m=[IO.MemoryStream]::new()
 try{$xml.Save($m);Write-Part $zip $name $m.ToArray()}finally{$m.Dispose()}
}
function Save-WordPicture($range,$path){
 [byte[]]$bits=$range.EnhMetaFileBits
 $stream=[IO.MemoryStream]::new($bits,$false)
 $meta=[Drawing.Imaging.Metafile]::new($stream)
 $width=2100
 $height=[Math]::Max(1,[int]($width*$meta.Height/$meta.Width))
 $bitmap=[Drawing.Bitmap]::new($width,$height)
 $g=[Drawing.Graphics]::FromImage($bitmap)
 try{
  $g.Clear([Drawing.Color]::White)
  $g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.DrawImage($meta,0,0,$width,$height)
  $bitmap.Save($path,[Drawing.Imaging.ImageFormat]::Png)
 }finally{$g.Dispose();$bitmap.Dispose();$meta.Dispose();$stream.Dispose()}
}
function Replace-Paragraph($xml,$prefix,$text){
 foreach($para in $xml.SelectNodes('//*[local-name()="p"]')){
  $nodes=$para.SelectNodes('./*[local-name()="r"]/*[local-name()="t"]')
  $value=($nodes | ForEach-Object {$_.InnerText}) -join ''
  if($value.StartsWith($prefix)){
   $nodes.Item(0).InnerText=$text
   for($i=1;$i -lt $nodes.Count;$i++){$nodes.Item($i).InnerText=''}
   return
  }
 }
}
function Patch-Report($file,$summary){
 $zip=[IO.Compression.ZipFile]::Open($file,[IO.Compression.ZipArchiveMode]::Update)
 try{
  [xml]$xml=Read-Part $zip 'word/document.xml'
  [xml]$rels=Read-Part $zip 'word/_rels/document.xml.rels'
  $pics=$xml.SelectNodes('//*[local-name()="inline"]')
  foreach($job in @($jobs | Where-Object {$_.Report -eq [IO.Path]::GetFileName($file)})){
   $pic=$pics.Item($job.Index-1)
   $blip=$pic.SelectSingleNode('.//*[local-name()="blip"]')
   $id=$blip.GetAttribute('embed',$R)
   $rel=$rels.DocumentElement.ChildNodes | Where-Object {$_.Id -eq $id} | Select-Object -First 1
   $target='word/'+$rel.Target
   $path=Join-Path (Join-Path $Root 'Связи') $job.Image
   Write-Part $zip $target ([IO.File]::ReadAllBytes($path))
   $linkId='rIdSync'+$job.Index
   $external=$rels.DocumentElement.ChildNodes | Where-Object {$_.Id -eq $linkId} | Select-Object -First 1
   if(!$external){
    $external=$rels.CreateElement('Relationship',$P)
    $external.SetAttribute('Id',$linkId)
    $external.SetAttribute('Type',$R+'/image')
    $rels.DocumentElement.AppendChild($external) | Out-Null
   }
   $external.SetAttribute('Target','Связи/'+$job.Image)
   $external.SetAttribute('TargetMode','External')
   $blip.SetAttribute('link',$R,$linkId) | Out-Null
   $desc=$pic.SelectSingleNode('./*[local-name()="docPr"]')
   $desc.SetAttribute('descr','Связанный снимок '+$job.Image)
   $image=[Drawing.Image]::FromFile($path)
   try{
    $height=[Math]::Min($job.Height,175*$image.Height/$image.Width)
    $width=$height*$image.Width/$image.Height
    $cx=[int64]($width*36000);$cy=[int64]($height*36000)
    $extent=$pic.SelectSingleNode('./*[local-name()="extent"]')
    $extent.SetAttribute('cx',[string]$cx);$extent.SetAttribute('cy',[string]$cy)
    foreach($e in $pic.SelectNodes('.//*[local-name()="xfrm"]/*[local-name()="ext"]')){
     $e.SetAttribute('cx',[string]$cx);$e.SetAttribute('cy',[string]$cy)
    }
   }finally{$image.Dispose()}
  }
  $name=[IO.Path]::GetFileName($file)
  if($name -eq '01_Отчёт.docx'){
   $sourceZip=[IO.Compression.ZipFile]::OpenRead((Join-Path $Root '01_План_квартиры.docx'))
   try{[xml]$source=Read-Part $sourceZip 'word/document.xml'}finally{$sourceZip.Dispose()}
   $sourceTable=$source.SelectSingleNode('//*[local-name()="body"]/*[local-name()="tbl"]')
   $old=$xml.SelectSingleNode('//*[local-name()="body"]/*[local-name()="tbl"]')
   $new=$xml.ImportNode($sourceTable,$true)
   # Keep the report table geometry while taking all rows from the source.
   foreach($tag in @('tblPr','tblGrid')){
    $before=$old.SelectSingleNode('./*[local-name()="'+$tag+'"]')
    $after=$new.SelectSingleNode('./*[local-name()="'+$tag+'"]')
    if($before -and $after){$new.ReplaceChild($before.CloneNode($true),$after) | Out-Null}
   }
   $paragraphs=$new.SelectNodes('.//*[local-name()="p"]')
   for($i=0;$i -lt $paragraphs.Count;$i++){
    $p=$paragraphs.Item($i)
    $properties=$p.SelectSingleNode('./*[local-name()="pPr"]')
    if(!$properties){$properties=$xml.CreateElement('w','pPr',$W);$p.PrependChild($properties) | Out-Null}
    $keep=$properties.SelectSingleNode('./*[local-name()="keepNext"]')
    if(!$keep){$keep=$xml.CreateElement('w','keepNext',$W);$properties.AppendChild($keep) | Out-Null}
    $value=if($i -eq $paragraphs.Count-1){'0'}else{'1'}
    $keep.SetAttribute('val',$W,$value) | Out-Null
   }
   $old.ParentNode.ReplaceChild($new,$old) | Out-Null
  }
  if($name -eq '04_Отчёт.docx'){
   Replace-Paragraph $xml 'Учебный набор содержит' $summary
  }
  Write-Xml $zip 'word/document.xml' $xml
  Write-Xml $zip 'word/_rels/document.xml.rels' $rels
 }finally{$zip.Dispose()}
}
$word=$null;$excel=$null
try{
 foreach($name in @('01_План_квартиры.docx','02_Семейное_дерево.docx','Копия 04_Оценки.xlsx','Копия 05_Справочник.xlsx','01_Отчёт.docx','02_Отчёт.docx','04_Отчёт.docx','05_Отчёт.docx')){
  if(!(Test-Path -LiteralPath (Join-Path $Root $name))){throw ('Не найден файл '+$name)}
  try{
   $probe=[IO.File]::Open((Join-Path $Root $name),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
   $probe.Dispose()
  }catch{throw ('Закройте файл "'+$name+'" в редакторе и повторите обновление.')}
 }
 New-Item -ItemType Directory -Path (Join-Path $Root 'Связи') -Force | Out-Null
 $word=New-Object -ComObject Word.Application
 $word.Visible=$false;$word.DisplayAlerts=0
 foreach($job in @(@{File='01_План_квартиры.docx';Out='01_План.png'},@{File='02_Семейное_дерево.docx';Out='02_Древо.png'})){
  Write-Output ($job.File+' -> снимок')
  $doc=$word.Documents.Open((Join-Path $Root $job.File),$false,$true)
  try{
   $range=$doc.Content.Duplicate
   if($job.File -eq '01_План_квартиры.docx'){$range=$doc.Sections.Item(1).Range.Duplicate}
   $range.End=$range.End-1
   Save-WordPicture $range (Join-Path (Join-Path $Root 'Связи') $job.Out)
  }finally{$doc.Close(0)}
 }
 $excel=New-Object -ComObject Excel.Application
 $excel.Visible=$false;$excel.DisplayAlerts=$false
 $summary=''
 foreach($job in @(
  @{File='Копия 04_Оценки.xlsx';Sheet='Оценки';Range='A1:L34';Out='04_Оценки.png'},
  @{File='Копия 05_Справочник.xlsx';Sheet='Содержание';Range='A1:J19';Out='05_Содержание.png'},
  @{File='Копия 05_Справочник.xlsx';Sheet='02_Раздел';Range='A1:J27';Out='05_Раздел.png'})){
  Write-Output ($job.File+' -> '+$job.Sheet)
  $book=$excel.Workbooks.Open((Join-Path $Root $job.File),0,$true)
  try{
   $excel.CalculateFull()
   $sheet=$book.Worksheets.Item($job.Sheet)
   $sheet.Activate() | Out-Null
   if($job.Out -eq '04_Оценки.png'){
    $grades=@()
    for($row=12;$row -le 25;$row++){
     $v=$sheet.Cells.Item($row,3).Value2
     if($null -ne $v -and [string]$v -ne ''){$grades+=[double]$v}
    }
    $threes=@($grades | Where-Object {$_ -eq 3}).Count
    $fours=@($grades | Where-Object {$_ -eq 4}).Count
    $fives=@($grades | Where-Object {$_ -eq 5}).Count
    $sum=($grades | Measure-Object -Sum).Sum
    if($grades.Count -eq 0){throw 'В таблице нет оценок для расчёта.'}
    $average=($sum/$grades.Count).ToString('F2',[Globalization.CultureInfo]::GetCultureInfo('ru-RU'))
    $summary="Учебный набор содержит оценок 3: $threes, оценок 4: $fours, оценок 5: $fives. Сумма оценок равна $sum, средний балл - $average. Круговая диаграмма использует результаты формул."
    # Use the same three point colours as the existing report preview.
    if($sheet.ChartObjects().Count -gt 0){
     $series=$sheet.ChartObjects(1).Chart.SeriesCollection(1)
     $colors=@(0xB4771F,0x0E7FFF,0x2CA02C)
     for($i=1;$i -le 3;$i++){$series.Points($i).Format.Fill.ForeColor.RGB=$colors[$i-1]}
    }
   }
   $range=$sheet.Range($job.Range)
   $range.CopyPicture(1,2) | Out-Null
   $chart=$sheet.ChartObjects().Add(0,0,$range.Width,$range.Height)
   try{
    $chart.Activate() | Out-Null
    $chart.Chart.Paste() | Out-Null
    $path=Join-Path (Join-Path $Root 'Связи') $job.Out
    if(!$chart.Chart.Export($path,'PNG')){throw 'Не удалось сохранить снимок таблицы.'}
   }finally{$chart.Delete()}
  }finally{$book.Close($false)}
 }
 $excel.Quit();$excel=$null
 foreach($name in @('01_Отчёт.docx','02_Отчёт.docx','04_Отчёт.docx','05_Отчёт.docx')){
  Patch-Report (Join-Path $Root $name) $summary
 }
 foreach($name in @('01_Отчёт.docx','02_Отчёт.docx','04_Отчёт.docx','05_Отчёт.docx')){
  $doc=$word.Documents.Open((Join-Path $Root $name),$false,$false)
  try{
   foreach($toc in $doc.TablesOfContents){$toc.Update()}
   $doc.Repaginate();$doc.Save()
   if($PdfFolder){
    New-Item -ItemType Directory -Path $PdfFolder -Force | Out-Null
    $doc.ExportAsFixedFormat((Join-Path $PdfFolder ([IO.Path]::GetFileNameWithoutExtension($name)+'.pdf')),17)
   }
   Write-Output ($name+' -> обновлён, страниц: '+$doc.ComputeStatistics(2))
  }finally{$doc.Close(0)}
 }
 Write-Output 'Готово. Отчёты обновлены из файлов этой папки.'
}finally{
 if($excel){try{$excel.Quit()}catch{}}
 if($word){try{$word.Quit()}catch{}}
}
