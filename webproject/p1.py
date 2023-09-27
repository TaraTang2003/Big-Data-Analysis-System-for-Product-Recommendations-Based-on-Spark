
# from collections import Counter
# ex = [1, 1, 3, 4, 4, 6]
# result = dict(Counter(ex))
# print(result)

import wordcloud as wc
from wordcloud import  ImageColorGenerator
from PIL import Image
import numpy as np
mask = np.array(Image.open("c.png"))
graph = np.array(mask)
#设置背景形状图片
# mask = np.array(Image.open("fivestar.png"))

#画图
word_cloud = wc.WordCloud(font_path="C:\Windows\Fonts\msyh.ttc",mask=graph,background_color='white')#字体、背景形状
word_cloud.generate("四行 代码 30分钟 集成 支持 多人 至 百万 四行 代码 30分钟 集成 支持 多人 至 百万 四行 代码 30分钟 集成 支持 多人 至 百万 四行 代码 30分钟 集成 支持 多人 至 百万")
image_color = ImageColorGenerator(graph)#生成词云的颜色
word_cloud.to_file("a.png")#绘制到一个图片里
